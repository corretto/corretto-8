### Instruction for generating Amazon Corretto 8 installer

There are two options to generate a Corretto installer.

#### Option 1: Build Corretto 8
* Under the root directory of the repository, run
```
./gradlew :installers:mac:pkg:generateInstaller
```

#### Option 2: Use pre-built Corretto 8 artifacts
* Set the environment variable "CORRETTO_ARTIFACTS_PATH" to the path of the pre-built Corretto artifacts.
```
export CORRETTO_ARTIFACTS_PATH=.../path/to/amazon-corretto-8.jdk
```
* Under the root directory of the repository, run
```
./gradlew :installers:mac:pkg:generateInstaller
```

The installer will be generated under "<repo-root>/installers/mac/pkg/corretto-build/distributions".
