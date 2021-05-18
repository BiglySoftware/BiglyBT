SDK = /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk
ARCHS = -arch arm64  -arch x86_64
LFLAGS = -bundle -isysroot $(SDK) $(ARCHS) -framework JavaNativeFoundation -framework AppKit -framework IOKit -mmacosx-version-min=10.14 -F/System/Library/Frameworks/
CFLAGS = -c $(ARCHS) -mmacosx-version-min=10.14
OBJECTS = OSXAccess.o IONotification.o LaunchServicesWrapper.o DisableAppNap.o


OUTLIB = libOSXAccess

all:
	g++ $(CFLAGS) OSXAccess.mm IONotification.m  LaunchServicesWrapper.m DisableAppNap.c
	g++ -o $(OUTLIB)_10.5.jnilib $(LFLAGS) $(OBJECTS)
	rm -f *.o

