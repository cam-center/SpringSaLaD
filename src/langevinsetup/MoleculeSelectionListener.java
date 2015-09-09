/*
 * This interface will be implemented by any class that wants to be notified
 * of changes to the DrawMolecule panel. 
 */

package langevinsetup;

public interface MoleculeSelectionListener {
    
    public void selectionOccurred(MoleculeSelectionEvent event);
}
