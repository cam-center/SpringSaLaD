/**
 * Should have implemented this long ago.  There are many GUI components which
 * must listen for a simulation. 
 */

package runlauncher;

public interface SimulationListener {

    public void simulationChanged(SimulationEvent event);
}
