Build Studio Libraries (app/libs/)

Compiler tools (ecj.jar, d8.jar, apksigner.jar) are packaged in:
  app/src/main/assets/bin/

Native AAPT2 binaries are packaged in:
  app/src/main/jniLibs/

This ensures zero duplicate files and allows Build Studio to extract and
run compilers seamlessly on any Android device without class conflicts.
