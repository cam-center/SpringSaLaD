package org.springsalad.clusteranalysis;

public class Cluster_OLD implements Comparable<Cluster_OLD>{
    /*
    public static void main(String[] args){
        Cluster.molecules = new String[]{"Nck", "NWASP", "nephrin"};

        //checking one constructor
        Cluster.startCluster();
        Cluster.addToCluster("Nck",1);
        Cluster.addToCluster("nephrin",1);
        Cluster.addToCluster("Nck",1);
        Cluster.addToCluster("Nck",1);
        Cluster.addToCluster("NWASP",1);
        Cluster c1 = Cluster.returnedVerifiedCluster(5);
        System.out.println(Arrays.toString(molecules));
        System.out.println(c1);

        // checking the other
        Cluster c2 = new Cluster(new int[]{1,3,1});
        System.out.println(c2 + ", of size " + c2.size);

        List<Cluster> listc = new ArrayList<>();
        listc.add(c1);
        listc.add(c2);
        Collections.sort(listc);
        System.out.println(listc);
    }
    */
    String[] molecules;

    int[] composition;
    int size;

    //use factory instead
    Cluster_OLD(String[] molecules, int[] comp){
        /*if (molecules == null)
            throw new IllegalStateException("Trying to create a cluster before setting the molecule types");
        if (comp.length != molecules.length){
            throw new IllegalArgumentException("Cannot understand the cluster composition given");
        }*/
        composition = comp;
        size = 0;
        for (int count: comp){
            size += count;
        }
    }

    public int compareTo(Cluster_OLD c){
        for (int i = 0; i < composition.length; i++){
            if (composition[i] != c.composition[i]){
                return (composition[i] < c.composition[i]) ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < composition.length-1; i++){
            sb.append(composition[i]).append(",");
        }
        sb.append(composition[composition.length-1]);
        return sb.toString();
    }
}
