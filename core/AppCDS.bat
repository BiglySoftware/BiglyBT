AppCDS allows classfiles to be pre-processed into an archive for faster loading 

https://openjdk.java.net/jeps/310

Remove our class loader in Main

//if (Launcher.checkAndLaunch(Main.class, args))
		//	return;

as this prevents bigly classes from being dumped


# c:\Projects\jdk-11.0.1\bin\java -Xshare:off -XX:+UseAppCDS -XX:DumpLoadedClassList=classes.lst -classpath BiglyBT.jar;swt-win64.jar;commons-cli.jar  com.biglybt.ui.Main

# c:\Projects\jdk-11.0.1\bin\java -Xshare:dump -XX:+UseAppCDS -XX:SharedClassListFile=classes.lst -XX:SharedArchiveFile=biglybt.jsa -classpath BiglyBT.jar;swt-win64.jar;commons-cli.jar

# c:\Projects\jdk-11.0.1\bin\java -Xshare:auto -XX:+UseAppCDS -XX:SharedArchiveFile=biglybt.jsa -classpath BiglyBT.jar;swt-win64.jar;commons-cli.jar  com.biglybt.ui.Main

c:\Projects\jdk-11.0.1\bin\java -classpath BiglyBT.jar;swt-win64.jar;commons-cli.jar  com.biglybt.ui.Main
