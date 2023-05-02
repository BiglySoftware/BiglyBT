# aereg

This is the implementation of native methods of [com.biglybt.platform.win32.access.impl.AEWin32AcessInterface](../../src/com/biglybt/platform/win32/access/impl/AEWin32AccessInterface.java) meant to provide access to certain native interfaces of Windows.

## Setting Up Dev Environment

You will need *Visual Studio 2022* with *Desktop development with C++* workload and the following individual components installed:
* *MSVC v143 - VS 2022 C++ x64/x86 build tools (Latest)* or another set of v143 build tools
* A version of *Windows 11 SDK* or *Windows 10 SDK*

Create a file named `Directory.Build.props` in the current directory with the following XML as its content, placing the path to your JDK installation in the `JDKPath` element:
```
<Project>
  <PropertyGroup>
    <JDKPath></JDKPath>
  </PropertyGroup>
</Project>
```

Building the project for the same platform as your JVM creates a `aereg.dll` file for Win32 or a `aereg64.dll` file for x64 which must be placed in the working directory when BiglyBT is started.
