/*
 * Copyright (C) 1999-2026 University of Connecticut Health Center
 *
 * Licensed under the MIT License (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *  http://www.opensource.org/licenses/mit-license.php
 */
/*
 * Ported verbatim from VCell (virtualcell/vcell), cbit.vcell.simdata.SpringSaladTrajectory,
 * which is also MIT licensed. Only the package declaration and this note differ. Both projects
 * parse the same solver output; if you fix a parsing bug here, send it back to VCell too.
 */
package org.springsalad.viewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory model of a SpringSaLaD (Langevin) particle trajectory, parsed from the
 * solver's viewer file ({@code SimID_<key>_0__VIEW_Run0.txt}).
 * <p>
 * The viewer file is written by the {@code LangevinNoVis01} solver every {@code dt_image}
 * step (for Run 0 only). It is a tab-delimited text file: a small header followed by a
 * sequence of {@code SCENE} blocks, one per time point, each listing every site's id,
 * radius, color and xyz position plus the links/bonds present at that instant. The grammar is
 * pinned by {@code ViewerTrajectoryFormatTest}.
 * <p>
 * This is the spatiotemporal data consumed by the SaLaD 3D renderer / movie player.
 */
public class SpringSaladTrajectory implements Serializable {

	private static final long serialVersionUID = 1L;

	/** A single site (glyph) snapshot within one frame. */
	public static class Site implements Serializable {
		private static final long serialVersionUID = 1L;
		private final int id;
		private final double radius;
		private final String color;
		private final double x;
		private final double y;
		private final double z;

		public Site(int id, double radius, String color, double x, double y, double z) {
			this.id = id;
			this.radius = radius;
			this.color = color;
			this.x = x;
			this.y = y;
			this.z = z;
		}
		public int getId() { return id; }
		public double getRadius() { return radius; }
		public String getColor() { return color; }
		public double getX() { return x; }
		public double getY() { return y; }
		public double getZ() { return z; }
	}

	/**
	 * What a site actually <em>is</em> — which molecule it belongs to and which site of that
	 * molecule it is. Read from the solver's {@code SiteIDs.csv}; see
	 * {@link #parseSiteIdentities}.
	 */
	public static class SiteIdentity implements Serializable {
		private static final long serialVersionUID = 1L;
		private final String moleculeName;
		private final int siteIndex;
		private final String siteTypeName;

		public SiteIdentity(String moleculeName, int siteIndex, String siteTypeName) {
			this.moleculeName = moleculeName;
			this.siteIndex = siteIndex;
			this.siteTypeName = siteTypeName;
		}
		/** The molecule (species) name, e.g. {@code MT0}. */
		public String getMoleculeName() { return moleculeName; }
		/**
		 * Index of this site within its molecule — the same index as the {@code SITE n} line of the
		 * solver input, hence the index into the model's {@code getComponentList()}.
		 */
		public int getSiteIndex() { return siteIndex; }
		/** The site type name, e.g. {@code Site0}. */
		public String getSiteTypeName() { return siteTypeName; }
	}

	/** One time point: the sites' positions and the links/bonds present at {@link #getTime()}. */
	public static class Frame implements Serializable {
		private static final long serialVersionUID = 1L;
		private final int sceneNumber;
		private final double time;
		private final List<Site> sites;
		/** Each link is an {@code int[]{idA, idB}}; includes both structural links and dynamic bonds. */
		private final List<int[]> links;

		public Frame(int sceneNumber, double time, List<Site> sites, List<int[]> links) {
			this.sceneNumber = sceneNumber;
			this.time = time;
			this.sites = Collections.unmodifiableList(sites);
			this.links = Collections.unmodifiableList(links);
		}
		public int getSceneNumber() { return sceneNumber; }
		public double getTime() { return time; }
		public List<Site> getSites() { return sites; }
		public List<int[]> getLinks() { return links; }
	}

	// header fields
	private final double totalTime;
	private final double dtImage;
	private final double xSize;
	private final double ySize;
	private final double zOutside;
	private final double zInside;
	private final List<Frame> frames;
	/**
	 * Site id to what that site is. Empty when the run's {@code SiteIDs.csv} was not available —
	 * simulations from before it was served, or whose solver folder has since been pruned. Added
	 * after the original release of this class, so it is null in anything deserialized from then.
	 */
	private final Map<Integer, SiteIdentity> siteIdentities;

	public SpringSaladTrajectory(double totalTime, double dtImage, double xSize, double ySize,
								 double zOutside, double zInside, List<Frame> frames) {
		this(totalTime, dtImage, xSize, ySize, zOutside, zInside, frames, Collections.emptyMap());
	}

	public SpringSaladTrajectory(double totalTime, double dtImage, double xSize, double ySize,
								 double zOutside, double zInside, List<Frame> frames,
								 Map<Integer, SiteIdentity> siteIdentities) {
		this.totalTime = totalTime;
		this.dtImage = dtImage;
		this.xSize = xSize;
		this.ySize = ySize;
		this.zOutside = zOutside;
		this.zInside = zInside;
		this.frames = Collections.unmodifiableList(frames);
		this.siteIdentities = Collections.unmodifiableMap(siteIdentities);
	}

	/** This trajectory with site identities attached; the receiver is left unchanged. */
	public SpringSaladTrajectory withSiteIdentities(Map<Integer, SiteIdentity> identities) {
		return new SpringSaladTrajectory(totalTime, dtImage, xSize, ySize, zOutside, zInside, frames,
				identities == null ? Collections.emptyMap() : identities);
	}

	public double getTotalTime() { return totalTime; }
	public double getDtImage() { return dtImage; }
	/** Bounding-box extents from the header; use these for the scene box (units are the model's). */
	public double getXSize() { return xSize; }
	public double getYSize() { return ySize; }
	public double getZOutside() { return zOutside; }
	public double getZInside() { return zInside; }
	public List<Frame> getFrames() { return frames; }
	public int getFrameCount() { return frames.size(); }

	/** Site id to identity; empty when the run did not supply {@code SiteIDs.csv}. */
	public Map<Integer, SiteIdentity> getSiteIdentities() {
		return siteIdentities == null ? Collections.emptyMap() : siteIdentities;
	}

	/** True when sites can be named, i.e. this run supplied {@code SiteIDs.csv}. */
	public boolean hasSiteIdentities() { return !getSiteIdentities().isEmpty(); }

	public SiteIdentity getSiteIdentity(int siteId) { return getSiteIdentities().get(siteId); }

	/**
	 * Identity of the site <em>type</em> a site belongs to — the unit the viewer shows and hides.
	 * <p>
	 * When the run supplied {@code SiteIDs.csv} this is the real (molecule, site type) pair. Failing
	 * that it falls back to the site's color and radius, which is all the trajectory file itself can
	 * distinguish sites by: both come from the site's {@code TYPE} in the solver input, so one
	 * (color, radius) pair is one site type — but two <em>different</em> molecules whose sites share
	 * a color and radius are then indistinguishable, and collapse into one entry.
	 */
	public String siteTypeKey(Site site) {
		// NOTE: VCell's copy has a literal NUL here ("site:MT0\0Site0") rather than a space. It is
		// latent there -- the key is only ever used as an opaque map/set key -- but this copy uses a
		// real space so the key is printable. Worth fixing upstream; see the PR.
		SiteIdentity identity = getSiteIdentity(site.getId());
		if (identity != null) {
			return "site:" + identity.getMoleculeName() + ' ' + identity.getSiteTypeName();
		}
		String color = site.getColor() == null ? "" : site.getColor().trim().toUpperCase(Locale.ROOT);
		return "color:" + color + '@' + String.format(Locale.ROOT, "%.5f", site.getRadius());
	}

	/**
	 * Parse a SpringSaLaD viewer file into a {@link SpringSaladTrajectory}.
	 *
	 * @param reader a reader over the viewer file contents (caller closes it)
	 * @return the parsed trajectory
	 * @throws IOException on read error or malformed header
	 */
	public static SpringSaladTrajectory parse(Reader reader) throws IOException {
		BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);

		double totalTime = 0, dtImage = 0, xSize = 0, ySize = 0, zOutside = 0, zInside = 0;
		boolean sawTotalTime = false;

		// ---- header: key\tvalue lines until the terminating blank line ----
		String line;
		while ((line = br.readLine()) != null) {
			if (line.trim().isEmpty()) {
				break; // blank line ends the header
			}
			String[] t = line.split("\t");
			if (t.length < 2) {
				continue;
			}
			String key = t[0].trim();
			double val = parseDouble(t[1]);
			switch (key) {
				case "TotalTime": totalTime = val; sawTotalTime = true; break;
				case "dtimage":   dtImage = val;   break;
				case "xsize":     xSize = val;     break;
				case "ysize":     ySize = val;     break;
				case "z_outside": zOutside = val;  break;
				case "z_inside":  zInside = val;   break;
				default: break; // tolerate unknown header keys
			}
		}
		if (!sawTotalTime) {
			throw new IOException("Not a SpringSaLaD viewer file: missing 'TotalTime' header");
		}

		// ---- frames: repeated SCENE blocks ----
		List<Frame> frames = new ArrayList<>();
		int sceneNumber = -1;
		double time = 0;
		List<Site> sites = new ArrayList<>();
		List<int[]> links = new ArrayList<>();
		boolean inScene = false;

		while ((line = br.readLine()) != null) {
			if (line.trim().isEmpty()) {
				continue; // blank lines separate frames; frame is flushed on next SCENE / EOF
			}
			String[] t = line.split("\t");
			String tag = t[0].trim();
			switch (tag) {
				case "SCENE":
					if (inScene) {
						frames.add(new Frame(sceneNumber, time, sites, links));
					}
					inScene = true;
					sceneNumber = -1;
					time = 0;
					sites = new ArrayList<>();
					links = new ArrayList<>();
					break;
				case "SceneNumber":
					// SceneNumber \t <n> \t CurrentTime \t <t>
					if (t.length >= 2) sceneNumber = (int) parseDouble(t[1]);
					if (t.length >= 4) time = parseDouble(t[3]);
					break;
				case "ID":
					// ID \t <id> \t <radius> \t <color> \t <x> \t <y> \t <z>
					if (t.length >= 7) {
						sites.add(new Site(
								(int) parseDouble(t[1]),
								parseDouble(t[2]),
								t[3].trim(),
								parseDouble(t[4]),
								parseDouble(t[5]),
								parseDouble(t[6])));
					}
					break;
				case "Link":
					// Link \t <idA> \t : \t <idB>
					if (t.length >= 4) {
						links.add(new int[] { (int) parseDouble(t[1]), (int) parseDouble(t[3]) });
					}
					break;
				default:
					break; // tolerate unknown lines
			}
		}
		if (inScene) {
			frames.add(new Frame(sceneNumber, time, sites, links));
		}

		return new SpringSaladTrajectory(totalTime, dtImage, xSize, ySize, zOutside, zInside, frames);
	}

	/**
	 * {@code <siteId>,<MoleculeName> Site <index> SiteType <SiteTypeName>} — one line per site, as
	 * written by the solver's {@code MySystem.writeSiteIDs()}.
	 */
	private static final Pattern SITE_ID_LINE =
			Pattern.compile("^\\s*(\\d+)\\s*,\\s*(.+?) Site (\\d+) SiteType (.+?)\\s*$");

	/**
	 * Parse the solver's {@code SiteIDs.csv}, which names every site the run ever created —
	 * including molecules created part-way through by creation reactions.
	 * <p>
	 * This is the authoritative mapping from a trajectory site back to a molecule and site of the
	 * model. It could instead be reconstructed from the site id, which the solver composes as
	 * {@code moleculeInstance * 10000 + siteIndex}, but that would bake in the solver's internal id
	 * scheme and its molecule creation order, and would mislabel silently if either ever changed.
	 *
	 * @param reader a reader over the file contents (caller closes it)
	 * @return site id to identity; empty if the file has no recognizable lines
	 */
	public static Map<Integer, SiteIdentity> parseSiteIdentities(Reader reader) throws IOException {
		BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
		Map<Integer, SiteIdentity> identities = new LinkedHashMap<>();
		String line;
		while ((line = br.readLine()) != null) {
			Matcher m = SITE_ID_LINE.matcher(line);
			if (!m.matches()) {
				continue; // tolerate blank or unexpected lines rather than failing the whole run
			}
			identities.put(Integer.valueOf(m.group(1)),
					new SiteIdentity(m.group(2), Integer.parseInt(m.group(3)), m.group(4)));
		}
		return identities;
	}

	private static double parseDouble(String s) {
		return Double.parseDouble(s.trim());
	}
}
