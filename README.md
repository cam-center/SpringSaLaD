

## Eclipse Setup for Windows

  * Start new Eclipse (choose new project folder when asked)
  * click upper right 'workbench'
  * in 'package explorer'->import->'projects from git'->'clone uri'->'https://github.com/jmasison/SpringSaLaD.git'->choose 'maven' when asked
  * After project imports there are errors, edit pom.xml
  * comment out entire dependency for ArtifactID 'langevin' (couldn't find on maven)
  * rt-click 'SpringSaLaD' main project->properties->'Java Build Path'->Libraries->'Add Jars...'->'SpringSaLaD/lib/LangevinNoVis01.jar'
  * back to Eclipse, all errors should be resolved
  * open class MainGUI and rt-click inside main(...) method and debug as java application


## How to debug the solver (LangevinNoVis01) from within the client (SpringSaLaD) in Eclipse

* Perform the Setup for the LangevinNoVis01 as described in its README.md and make sure it runs standalone
* Open the SpringSaLaD project in Eclipse
    * In Project . Properties > Java Build Path > Projects > Add... : add the Langevin Project
    * In Project . Properties > Java Build Path > Libraries select the Langevin project jar file from the springsalad installation and Remove it
    * Modify the Debug Configuration > Source > Add... : add the langevin project (so that Eclipse can see the source code)
    * Modify the RunLauncher.java to directly run langevinnovis01.Global.main() instead of creating the process (commented out code example provided). Only tested for 1 single run.


