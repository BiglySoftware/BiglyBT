
104_SDK = /Developer/SDKs/MacOSX10.4u.sdk
104_ARCHS = -arch ppc -arch i386
104_LFLAGS = -bundle -isysroot $(104_SDK) $(104_ARCHS) -framework Foundation -framework JavaVM -framework Carbon -framework AppKit -framework IOKit
104_CFLAGS = -c $(104_ARCHS) -I $(104_SDK)/System/Library/Frameworks/ApplicationServices.framework/Versions/A/Frameworks/AE.framework/Versions/A/Headers

105_SDK = /Developer-3.2.6/SDKs/MacOSX10.5.sdk
105_ARCHS = -arch i386 -arch x86_64
105_LFLAGS = -bundle -isysroot $(105_SDK) $(105_ARCHS) -framework JavaVM -framework AppKit -framework IOKit  -mmacosx-version-min=10.5
105_CFLAGS = -c $(105_ARCHS) -I $(104_SDK)/System/Library/Frameworks/ApplicationServices.framework/Versions/A/Frameworks/AE.framework/Versions/A/Headers


105_OBJECTS = OSXAccess.o IONotification.o LaunchServicesWrapper.o
104_OBJECTS = OSXAccess.o IONotification.o
OUTLIB = libOSXAccess

all:
	echo 10.4 10.5


10.4:
	g++ $(104_CFLAGS) OSXAccess.mm IONotification.m  -DCARBON
	g++ -o $(OUTLIB).jnilib $(104_LFLAGS) $(104_OBJECTS)
	rm -f *.o

10.5:
	g++ $(105_CFLAGS) OSXAccess.mm IONotification.m  LaunchServicesWrapper.m
	g++ -o $(OUTLIB)_10.5.jnilib $(105_LFLAGS) $(105_OBJECTS)
	rm -f *.o

