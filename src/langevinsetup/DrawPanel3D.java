/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package langevinsetup;

import com.sun.j3d.utils.picking.*;
import com.sun.j3d.utils.behaviors.vp.*;
import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.*;
import helpersetup.Colors;

import javax.media.j3d.*;
import javax.vecmath.*;

import java.awt.*;
import java.awt.event.*;

import java.util.ArrayList;
import java.util.HashMap;

public class DrawPanel3D extends Canvas3D implements MouseListener, 
        KeyListener, MoleculeSelectionListener {

    // The molecule we're showing
    private final Molecule molecule;
    
    // Arrays of the current spheres and cylinders
    private final ArrayList<SiteSphere> spheres = new ArrayList<>();
    private final ArrayList<LinkCylinder> cylinders = new ArrayList<>();
    
    /* ********  Arrays of selected sites and links ******/
    private final ArrayList<Site> selectedSites = new ArrayList<>();
    private final ArrayList<Link> selectedLinks = new ArrayList<>();
    
    /* *******  Map from site to sphere  **/
    // The sphere holds a reference to the site, so it's easy to go the other way.
    private final HashMap<Site, SiteSphere> siteToSphere = new HashMap<>(100);
    
    /* ******* Map from link to cylinder **/
    private final HashMap<Link, LinkCylinder> linkToCylinder = new HashMap<>(100);
    
    /* We need a simple universe, a branchgroup, and a bounding sphere */
    SimpleUniverse su;
    BranchGroup rootBG;
    BoundingSphere bounds;
    
    /* ******* The branchgroup holding the membrane ********************/
    private final BranchGroup membraneGroup = new BranchGroup();
    
    /* **** A pick canvas to pick with the mouse *******/
    private final PickCanvas pickCanvas;
    
    /* **** Easiest to make the graphics configuration static *******/
    static GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
    
    // Pre-defined vectors
    public final static Vector3d x_axis = new Vector3d(1.0, 0, 0);
    public final static Vector3d y_axis = new Vector3d(0, 1.0, 0);
    public final static Vector3d z_axis = new Vector3d(0, 0, 1.0);
    
    // Membrane size
    private float xsize = 25;  // half the membrane size
    private float ysize = 25;  // half the membrane size
    
    /* Boolean to indicate if the ctrl key is pressed */
    private boolean ctrlPressed = false;
    
    /* ********* Array of MoleculeSelectionListeners to notify *******/
    private final ArrayList<MoleculeSelectionListener> listeners = new ArrayList<>();
    
    public DrawPanel3D(Molecule molecule){
        super(config);
        this.setPreferredSize(new Dimension(1000,500));
        //this.molecule = molecule;
        
        this.molecule = molecule;
        if(molecule.hasAnchorSites()){
            molecule.translate(0, 0, -molecule.membranePosition());
        }
        
        // Adjust the size of the membrane to contain the molecule's anchors
        for(Site site : molecule.getAnchorSites()){
            if(Math.abs(site.getX() + site.getRadius()) > xsize){
                xsize = (float)Math.abs(site.getX() + site.getRadius());
            }
            if(Math.abs(site.getY() + site.getRadius()) > ysize){
                ysize = (float)Math.abs(site.getY() + site.getRadius());
            }
        }
        xsize = 1.1f*xsize;
        ysize = 1.1f*ysize;
        
        su = new SimpleUniverse(this);
        rootBG = new BranchGroup();
        rootBG.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        rootBG.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        rootBG.setCapability(BranchGroup.ALLOW_DETACH);
        
        makeMembrane();
        
        // View properties
        View view = this.getView();
        view.setFrontClipDistance(0.001);
        view.setBackClipDistance(4000);
        
        // bounding sphere
        bounds = new BoundingSphere(new Point3d(0,0,0), 10000f);
        
        // make directional white light
        Vector3f lightDirection = new Vector3f(-1.0f, -2.0f, -1.0f);
        DirectionalLight light1 = new DirectionalLight(Colors.WHITE3D, lightDirection);
        light1.setInfluencingBounds(bounds);
        rootBG.addChild(light1);
        
        // make ambient dim light
        AmbientLight ambientLight = new AmbientLight(Colors.GRAY3D);
        ambientLight.setInfluencingBounds(bounds);
        rootBG.addChild(ambientLight);
        
        setInitialPosition(new Point3d(-150, 50, 150));
        setOrbitControls();
        
        su.addBranchGraph(rootBG);
        
        pickCanvas = new PickCanvas(this, rootBG);
        pickCanvas.setMode(PickCanvas.GEOMETRY);
        
        moleculeSetUp();
        
        // This panel should listen for its own events
        this.addKeyListener(this);
        this.addMouseListener(this);
        
    }
    
    private void moleculeSetUp(){
        for(Site site : molecule.getSiteArray()){
            addSite(site);
        }
        for(Link link : molecule.getLinkArray()){
            addLink(link);
        }
    }
    
    private void setInitialPosition(Point3d point){
        ViewingPlatform vp = su.getViewingPlatform();
        TransformGroup vpTG = vp.getViewPlatformTransform();
        
        Transform3D vpt3d = new Transform3D();
        vpTG.getTransform(vpt3d);
        
        vpt3d.lookAt(point, new Point3d(0,0,0), new Vector3d(0,1,0));
        vpt3d.invert();
        vpTG.setTransform(vpt3d);
    }
    
    private void setOrbitControls(){
        OrbitBehavior orbit = new OrbitBehavior(this, OrbitBehavior.REVERSE_ROTATE | OrbitBehavior.PROPORTIONAL_ZOOM | OrbitBehavior.STOP_ZOOM | OrbitBehavior.REVERSE_TRANSLATE);
        orbit.setSchedulingBounds(bounds);
        orbit.setMinRadius(2.0);
        orbit.setTransFactors(100.0, 100.0);
        su.getViewingPlatform().setViewPlatformBehavior(orbit);
    }
    
    private void makeMembrane(){
        float zmem = 0.1f;
        Appearance boxTopAp = new Appearance();
        Appearance boxSideAp = new Appearance();
        Color3f yellow = Colors.YELLOW3D;
        Color3f black = Colors.BLACK3D;
        Color3f cyan = Colors.CYAN3D;
        Material top = new Material(yellow, black, yellow, black, 50.0f);
        Material sides = new Material(cyan, black, cyan, black, 30.0f);
        boxTopAp.setMaterial(top);
        boxSideAp.setMaterial(sides);
        Box box = new Box(xsize, ysize, zmem, boxSideAp);
        box.setPickable(false);
        box.getShape(Box.FRONT).setAppearance(boxTopAp);
        
        // Make box transformgroup
        Transform3D boxt3d = new Transform3D();
        boxt3d.setTranslation(new Vector3f(0f,0f,-zmem));
        TransformGroup boxTG = new TransformGroup(boxt3d);
        
        boxTG.addChild(box);
        membraneGroup.addChild(boxTG);
    }
    
    public void addMembrane(boolean bool){
        if(bool){
            rootBG.addChild(membraneGroup);
        } else {
            membraneGroup.detach();
        }
    }
    
    private Group makeAxis(int length, Color col){
        TransformGroup topTG = new TransformGroup();
        // All these will glow
        Appearance ap = new Appearance();
        ap.setMaterial(new Material(new Color3f(col), Colors.BLACK3D, new Color3f(col), Colors.BLACK3D, 40.0f));
        // Make a cylinder
        Cylinder y_cyl = new Cylinder(0.1f, 10.0f, ap);
        for(int i=-length/2;i<=length/2;i++){
            Vector3d shift = new Vector3d(0,i,0);
            Transform3D t3d = new Transform3D();
            t3d.setTranslation(shift);
            TransformGroup xTG = new TransformGroup(t3d);
            xTG.addChild(new Cone(0.2f, 0.5f, ap));
            topTG.addChild(xTG);
        }
        
        topTG.addChild(y_cyl);
        
        return topTG;
        
    }
    
    private BranchGroup makeAxes(int length){
        TransformGroup topTG = new TransformGroup();
        // Add y-axis directly
        topTG.addChild(makeAxis(length, Color.green));
        // Make and add x-axis
        Transform3D xt3d = new Transform3D();
        xt3d.setRotation(new AxisAngle4d(z_axis, -Math.PI/2));
        TransformGroup xTG = new TransformGroup(xt3d);
        xTG.addChild(makeAxis(length,Color.blue));
        topTG.addChild(xTG);
        
        // Make and add x-axis
        Transform3D zt3d = new Transform3D();
        zt3d.setRotation(new AxisAngle4d(x_axis, Math.PI/2));
        TransformGroup zTG = new TransformGroup(zt3d);
        zTG.addChild(makeAxis(length,Color.orange));
        topTG.addChild(zTG);
        
        BranchGroup tempBG = new BranchGroup();
        tempBG.addChild(topTG);
        return tempBG;
    }
    
    public void systemSetup(){
        addMembrane(molecule.getLocation().equals(SystemGeometry.MEMBRANE));
        rootBG.addChild(makeAxes(10));
    }
    
    private void unHighlightSelected(){
        for(Site aSite : selectedSites){
            siteToSphere.get(aSite).highlight(false);
        }
        for(Link link : selectedLinks){
            linkToCylinder.get(link).highlight(false);
        }
    }
    
    private void clearSelectedLists(){
        selectedSites.clear();
        selectedLinks.clear();
    }
    
    /* ************ MOUSE LISTENER METHODS ******************************/
    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        pickCanvas.setShapeLocation(e);
        PickResult result = pickCanvas.pickClosest();
        
        if(result != null){      
            Primitive p = (Primitive)result.getNode(PickResult.PRIMITIVE);
            
            try{
                Sphere aSphere = (Sphere) p;
                for(SiteSphere sphere : spheres){
                    if(sphere.getSphere() == aSphere){
                        // System.out.println("Found sphere.");
                        Site site = sphere.getSite();
                        if(ctrlPressed){
                            sphere.highlight(!sphere.isHighlighted());
                            if(!selectedSites.contains(site)){
                                selectedSites.add(site);
                            } else {
                                selectedSites.remove(site);
                            }
                        } else {
                            if(sphere.isHighlighted()){
                                unHighlightSelected();
                                clearSelectedLists();
                            } else {
                                unHighlightSelected();
                                clearSelectedLists();
                                sphere.highlight(true);
                                selectedSites.add(site);
                            }
                        }
                        notifyListeners();
                        break;
                    }
                }
                
            } catch(ClassCastException cce){
                // Not a sphere. Do nothing.
            }
            
            try{
                Cylinder aCylinder = (Cylinder)p;
                for(LinkCylinder cylinder : cylinders){
                    if(cylinder.getCylinder() == aCylinder){
                        Link link = cylinder.getLink();
                        if(ctrlPressed){
                            cylinder.highlight(!cylinder.isHighlighted());
                            if(!selectedLinks.contains(link)){
                                selectedLinks.add(link);
                            } else {
                                selectedLinks.remove(link);
                            }
                        } else {
                            if(cylinder.isHighlighted()){
                                unHighlightSelected();
                                clearSelectedLists();
                            } else {
                                unHighlightSelected();
                                clearSelectedLists();
                                cylinder.highlight(true);
                                selectedLinks.add(link);
                            }
                        }
                        break;
                    }
                }
            } catch(ClassCastException cce){
                // Not a cylinder. Do nothing.
            }
        } else {
            if(!ctrlPressed){
                unHighlightSelected();
                clearSelectedLists();
            }
        }
        notifyListeners();
    }

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
    
    /* ************** KEY LISTENER METHODS **************************/
    
    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if(e.isControlDown() && !ctrlPressed){
            ctrlPressed = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if(ctrlPressed){
            ctrlPressed = false;
        }
    }
    
     /* ************ LISTEN FOR MOLECULE SELECTION EVENTS ************/
    
    @Override
    public void selectionOccurred(MoleculeSelectionEvent event){
        if(rootBG != membraneGroup.getParent()){
            addMembrane(molecule.getLocation().equals(SystemGeometry.MEMBRANE));
        }
        ArrayList<Site> currentSites = molecule.getSiteArray();
        ArrayList<Link> currentLinks = molecule.getLinkArray();
        // See if any sites were removed
        for(SiteSphere sphere : spheres){
            if(!currentSites.contains(sphere.getSite())){
                sphere.detach();
                siteToSphere.put(sphere.getSite(), null);
            }
        }
        // See if any links were removed
        for(LinkCylinder cylinder : cylinders){
            if(!currentLinks.contains(cylinder.getLink())){
                cylinder.detach();
                linkToCylinder.put(cylinder.getLink(), null);
            }
        }
        
        // Add any new sites or links
        for(Site site : molecule.getSiteArray()){
            if(siteToSphere.get(site) == null){
                addSite(site);
            }
        }
        for(Link link : molecule.getLinkArray()){
            if(linkToCylinder.get(link) == null){
                addLink(link);
            }
        }
        
        unHighlightSelected();
        clearSelectedLists();
        selectedSites.addAll(event.getSelectedSites());
        selectedLinks.addAll(event.getSelectedLinks());
        System.out.println("selectedSites =  " + selectedSites);
        for(Site site : selectedSites){
            siteToSphere.get(site).highlight(true);
        }
        for(Link link : selectedLinks){
            linkToCylinder.get(link).highlight(true);
        }
        this.repaint();
    }
    
    /* MOLECULE SELECTION LISTENER NOTIFICATION AND RELATED METHODS *********/
    
    public void addMoleculeSelectionListener(MoleculeSelectionListener listener){
        listeners.add(listener);
    }
    
    public void removeMoleculeSelectionListener(MoleculeSelectionListener listener){
        listeners.remove(listener);
    }
    
    private void notifyListeners(){
        MoleculeSelectionEvent event = new MoleculeSelectionEvent(selectedSites, selectedLinks);
        for(MoleculeSelectionListener listener : listeners){
            listener.selectionOccurred(event);
        }
    }
    
    /* ************* ADD AND REMOVE SITES ****************************/
    
    public void addSite(Site site){
        SiteSphere sphere = new SiteSphere(site);
        
        siteToSphere.put(site, sphere);
        spheres.add(sphere);
        
        rootBG.addChild(sphere);
    }
    
    
    /* ***************** ADD AND REMOVE LINKS ****************************/
    
    public void addLink(Link link){
        
        LinkCylinder cylinder = new LinkCylinder(link);
        
        linkToCylinder.put(link, cylinder);
        cylinders.add(cylinder);
        
        rootBG.addChild(cylinder);
        
    }
    
    
    /* ************ SHIFT SELECTED SITES *****************************/
    
    public void shiftSites(double dx, double dy, double dz){
       
        for(Site site : selectedSites){
            site.setX(site.getX() + dx);
            site.setY(site.getY() + dy);
            if(!site.getTypeName().equals(SiteType.ANCHOR)){
                site.setZ(site.getZ() + dz);
            }
            Vector3d vec = new Vector3d(site.getX(), site.getY(), site.getZ());
            SiteSphere sphere = siteToSphere.get(site);
            sphere.translate(vec);
        }
        
        for(Link link : molecule.getLinkArray()){
            LinkCylinder cylinder = linkToCylinder.get(link);
            cylinder.updatePosition();
        }
        
        notifyListeners();
        
    }

}
