## Instruction of Building Corretto8 on Windows

Corretto8 on Windows includes one subproject.

The `:zip` project builds and packages Corretto8 archive as zip file. Before executing 
this project, you need to provide the following parameters: 

```$xslt
bootjdk_dir         # Path of bootstrap JDK. To build Corretto8, JDK7 or JDK8 is required.

vcruntime_dir       # Path of the latest msvcr120.dll

msvcp_dir           # Path of the latest msvcp120.dll

freetype_dir        # Path of freetype
```

To execute this, run: `./gradlew :installers:windows:zip:build` at `corretto-8` root directory. 
The zip archives of Corretto8 JDK and JRE are located at `/installers/windows/zip/corretto-build/distributions`

```$xslt
➜ ./gradlew :installers:windows:zip:build \
            -Pbootjdk_dir=... \
            -Pvcruntime_dir=... \
            -Pmsvcp_dir=... \
            -Pfreetype_dir=...
```

Base on the architecture of your system, the execution generates `x64` or `x86` artifacts.

```$xslt
➜ tree installers/windows/zip/corretto-build/distributions
   installers/windows/zip/corretto-build/distributions
   └── unsigned-jdk-image.x86.zip
   └── unsigned-jre-image.x86.zip
```

```$xslt
➜ tree installers/windows/zip/corretto-build/distributions
   installers/windows/zip/corretto-build/distributions
   └── unsigned-jdk-image.x64.zip
   └── unsigned-jre-image.x64.zip
```

