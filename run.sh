#!/bin/bash

# Define the classpath including the dependencies and the application jar
CLASSPATH="./target/dependency/*:./target/springsalad-0.0.1-SNAPSHOT.jar"

# Define the main class
MAIN_CLASS="org.springsalad.langevinsetup.MainGUI"

# Run the application. No --add-exports needed: that was for Java3D/jogl reaching
# into sun.awt internals, and nothing renders with Java3D any more.
java -cp $CLASSPATH $MAIN_CLASS
