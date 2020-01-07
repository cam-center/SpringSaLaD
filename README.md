

## Eclipse Setup

  * Start new Eclipse (choose new project folder when asked)
  * click upper right 'workbench'
  * in 'package explorer'->import->'projects from git'->'clone uri'->'https://github.com/jmasison/SpringSaLaD.git'->choose 'maven' when asked
  * After project imports there are errors, edit pom.xml
  * comment out entire dependency for ArtifactID 'langevin' (couldn't find on maven)
  * rt-click 'SpringSaLaD' main project->properties->'Java Build Path'->Libraries->'Add Jars...'->'SpringSaLaD/lib/LangevinNoVis01.jar'
  * back to Eclipse, all errors should be resolved
  * open class MainGUI and rt-click inside main(...) method and debug as java application


