@IF %1. == . GOTO NODIR
@IF NOT EXIST %1 GOTO NODIR

@SET JAVABIN=%1
@IF EXIST %JAVABIN%\bin SET JAVABIN=%1\bin
@SET BIN=..\..\bin
@IF NOT %2. == . SET BIN=%2

%JAVABIN%\javah -d . -classpath %BIN% com.biglybt.platform.win32.access.impl.AEWin32AccessInterface
@GOTO :END

:NODIR
@echo %0 "path to java" [classpath dir]
:END