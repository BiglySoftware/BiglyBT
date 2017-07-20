//
//  IONotification.m
//  USBPrivateDataSample
//
//  Created by Vuze on 8/11/09.
/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

#include <IOKit/IOKitLib.h>
#include <IOKit/IOMessage.h>
#include <IOKit/IOCFPlugIn.h>
#include <IOKit/usb/IOUSBLib.h>
#include <IOKit/storage/IOMedia.h>
#include <IOKit/IOBSD.h>
#include <sys/mount.h>
#include <JavaVM/jni.h>

#import "IONotification.h"

extern void notify(const char *mount, io_service_t service, struct statfs *fs, bool added);
extern void notifyURL(const char *url);
extern void notifyURL2(const char *url);

// When a device is added, call IOServiceAddInterestNotification
// and monitor device removal.  Not really needed since NSWorkspaceDidUnmountNotification
// does an okay job (although it can't retrieve a ioservice)
//#define IONOTIFYDISMOUNT 1

#define _PATH_DEV       "/dev/"

void RawDeviceAdded(void* refcon, io_iterator_t iterator)
{
    [(IONotification*)refcon rawDeviceAdded:iterator];
}


/**
 * lookup a disk based on "dev" mount and return fileSystem Status
 *
 **/
static struct statfs * getFileSystemStatusDevMount(char * disk) {
	struct statfs * mountList;
	int mountListCount;
	int mountListIndex;

	mountListCount = getmntinfo(&mountList, MNT_NOWAIT);

	for (mountListIndex = 0; mountListIndex < mountListCount; mountListIndex++) {
		//fprintf(stderr, "looking for %s mounto %s fr %s\n ", disk, mountList[mountListIndex].f_mntonname, mountList[mountListIndex].f_mntfromname);
		if (strncmp(mountList[mountListIndex].f_mntfromname, _PATH_DEV, strlen(_PATH_DEV)) == 0) {
			if (strcmp(mountList[mountListIndex].f_mntfromname + strlen(_PATH_DEV), disk) == 0) {
				break;
			}
		}
	}

	return (mountListIndex < mountListCount) ? (mountList + mountListIndex) : (NULL);
}

/**
 * Lookup a disk based on "Volumes" mount point and return filesystem status
 *
 **/
static struct statfs * getFileSystemStatusFromMount(const char * mount) {
	struct statfs * mountList;
	int mountListCount;
	int mountListIndex;

	mountListCount = getmntinfo(&mountList, MNT_NOWAIT);

	for (mountListIndex = 0; mountListIndex < mountListCount; mountListIndex++) {
		//fprintf(stderr, "test mounto %s fr %s\n ",  mountList[mountListIndex].f_mntonname, mountList[mountListIndex].f_mntfromname);
		if (strcmp(mountList[mountListIndex].f_mntonname, mount) == 0) {
			break;
		}
	}

	return (mountListIndex < mountListCount) ? (mountList + mountListIndex) : (NULL);
}


void print( NSDictionary *map ) {
    NSEnumerator *enumerator = [map keyEnumerator];
    void * key;

    while ( key = [enumerator nextObject] ) {
        fprintf(stderr, "%p => %s\n", key, (char *) [map objectForKey: key]  );
    }
}


@implementation IONotification

void DeviceRemoved(void *refCon, io_iterator_t iterator) {
	kern_return_t kr;
	io_service_t service;

	while ((service = IOIteratorNext(iterator))) {
		CFTypeRef str_bsd_path = IORegistryEntryCreateCFProperty(service, CFSTR(kIOBSDNameKey), kCFAllocatorDefault, 0);

		if (str_bsd_path != NULL) {
			int len = CFStringGetLength(str_bsd_path) * 2 + 1;
			char s[len];
			CFStringGetCString((CFStringRef) str_bsd_path, s, len, kCFStringEncodingUTF8);

			io_name_t deviceName;
			kern_return_t kr = IORegistryEntryGetName(service, deviceName);
			fprintf(stderr, "DR %s -- %s\n", s, deviceName);

			struct statfs *fs = 0;
			StatfsObject *fso = [map objectForKey:(NSString *)str_bsd_path];
			fprintf(stderr, "found key as %p\n", fso);
			if (fso) {
				[map removeObjectForKey:(NSString *)str_bsd_path];
				fs = fso->fs;

				const char *mount = (fs == 0) ? 0 : fs->f_mntonname;
				notify(mount, service, fs, false);
			}

			CFRelease(str_bsd_path);
		}
		kr = IOObjectRelease(service);
	}
}

- (void) setupLight
{
	
	NSAppleEventManager *appleEventManager = [NSAppleEventManager sharedAppleEventManager];
	if (appleEventManager) {
        fprintf(stderr, "setEventHandler for handleGetURLEvent2\n");
		[appleEventManager setEventHandler:self andSelector:@selector(handleGetURLEvent2:withReplyEvent:) forEventClass:kInternetEventClass andEventID:kAEGetURL];
	}
}

- (void) setup
{
	
	NSAppleEventManager *appleEventManager = [NSAppleEventManager sharedAppleEventManager];
	if (appleEventManager) {
        fprintf(stderr, "setEventHandler for handleGetURLEvent\n");
		[appleEventManager setEventHandler:self andSelector:@selector(handleGetURLEvent:withReplyEvent:) forEventClass:kInternetEventClass andEventID:kAEGetURL];
	}

	

	SInt32 majorVersion,minorVersion;

	Gestalt(gestaltSystemVersionMajor, &majorVersion);
	Gestalt(gestaltSystemVersionMinor, &minorVersion);
	useNSWorkspace = majorVersion > 10 || (majorVersion == 10 && minorVersion > 4);
	
	fprintf(stderr, "useNSWorkspace = %d\n", useNSWorkspace);


	// We need a pool otherwise we get warnings of memory leaks
	// Should probably dispose of the pool on shutdown, however, since
	// this library is currently used during the life of the app, it's assumed
	// that when the app shuts down, the pool auto-shuts down.
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

	map = [[NSMutableDictionary alloc] init];
	
	if (!useNSWorkspace) {
		// port for service notifications
		gNotifyPort = IONotificationPortCreate(kIOMasterPortDefault);
		// We don't get service notifications if we don't set up a runLoop
		CFRunLoopSourceRef runLoopSource = IONotificationPortGetRunLoopSource(gNotifyPort);
		CFRunLoopRef runLoop = CFRunLoopGetCurrent();
		CFRunLoopAddSource(runLoop, runLoopSource, kCFRunLoopDefaultMode);
	} else {
		// Setup mount/unmount triggers
		NSWorkspace *ws = [NSWorkspace sharedWorkspace];
		NSNotificationCenter *center = [ws notificationCenter];
		[center addObserver:self selector:@selector(mount:) name:NSWorkspaceDidMountNotification object:ws];
		// TODO: Try NSWorkspaceWillUnmountNotification to see if we can get a statfs
		[center addObserver:self selector:@selector(unmount:) name:NSWorkspaceDidUnmountNotification object:ws];
	}

	[ self checkExisting ];
}

-(int)checkExisting
{
	CFMutableDictionaryRef matchingDict;
	kern_return_t kr;
	io_iterator_t iter;

	if (!useNSWorkspace) {
		matchingDict = IOServiceMatching(kIOMediaClass);
		if (matchingDict) {
			kr = IOServiceAddMatchingNotification(gNotifyPort, kIOTerminatedNotification,
					matchingDict, DeviceRemoved, NULL, &iter);
			io_service_t service;
			while (service = IOIteratorNext(iter)) {
				IOObjectRelease(service);
			}
		}

		// Hookup mediaAdded service notification.
		matchingDict = IOServiceMatching(kIOMediaClass);
		if (matchingDict == NULL) {
			fprintf(stderr, "IOServiceMatching returned NULL.\n");
			return -1;
		}

		kr = IOServiceAddMatchingNotification(gNotifyPort, kIOFirstMatchNotification,
				matchingDict, RawDeviceAdded, (void *)self, &iter);
	} else {
		// Get list of IOMedia and notify
		matchingDict = IOServiceMatching(kIOMediaClass);
		kr = IOServiceGetMatchingServices(kIOMasterPortDefault, matchingDict, &iter);
	}

	[self rawDeviceAdded:iter];

	return 0;
}


#ifdef IONOTIFYDISMOUNT
void DeviceNotification(void *refCon, io_service_t service, natural_t messageType, void *messageArgument) {
	io_name_t deviceName;
	kern_return_t kr = IORegistryEntryGetName(service, deviceName);
	fprintf(stderr, "Device %s Not %d.\n", deviceName, messageType);
	if (messageType == kIOMessageServiceIsTerminated || messageType == kIOMessageServiceWasClosed) {
		if (messageType == kIOMessageServiceWasClosed) {
			fprintf(stderr, "Device close.\n");
		} else {
			fprintf(stderr, "Device terminated.\n");
		}

		CFTypeRef str_bsd_path = IORegistryEntryCreateCFProperty(service, CFSTR(kIOBSDNameKey), kCFAllocatorDefault, 0);

		if (str_bsd_path != NULL) {
			int len = CFStringGetLength(str_bsd_path) * 2 + 1;
			char s[len];
			CFStringGetCString((CFStringRef) str_bsd_path, s, len, kCFStringEncodingUTF8);
			CFRelease(str_bsd_path);

			fprintf(stderr, "WTF %s\n", s);

			struct statfs *fs = getFileSystemStatusDevMount(s);
			const char *mount = (fs == 0) ? 0 : fs->f_mntonname;
			notify(mount, service, fs, false);
		}

		if (refCon) {
			IOObjectRelease((io_object_t) refCon);
		}
	}
}
#endif

- (void) handleTimer: (NSTimer *) timer {
	CFTypeRef str_bsd_path = [timer userInfo];
	int len = CFStringGetLength(str_bsd_path) * 2 + 1;
	char s[len];
	CFStringGetCString((CFStringRef) str_bsd_path, s, len, kCFStringEncodingUTF8);

	struct statfs *fs = getFileSystemStatusDevMount(s);
	if (fs) {
		StatfsObject *fso = [StatfsObject new];
		fso->fs = fs;
		[map setObject:fso forKey:(NSString *)str_bsd_path];

		CFMutableDictionaryRef matchingDict;

		matchingDict = IOServiceMatching(kIOMediaClass);
		if (matchingDict) {
			io_service_t service;
			char *sBSDName = strrchr(fs->f_mntfromname, (int) '/');
			if (sBSDName) {
				sBSDName++;

				fprintf(stderr, "Searching for %s\n", sBSDName);
				CFStringRef bsdname = CFStringCreateWithCString(kCFAllocatorDefault, sBSDName, kCFStringEncodingMacRoman);

				CFDictionarySetValue(matchingDict, CFSTR(kIOBSDNameKey), bsdname);
				service = IOServiceGetMatchingService(kIOMasterPortDefault, matchingDict);
			}

			notify(fs->f_mntonname, service, fs, true);
			if (service) {
				IOObjectRelease(service);
			}
		}
	}
	
	fprintf(stderr, "timer hit %s\n", s);
}

- (void)rawDeviceAdded:(io_iterator_t)iterator {
	kern_return_t kr;
	io_service_t service;

	while ((service = IOIteratorNext(iterator))) {
		io_name_t deviceName;
		kr = IORegistryEntryGetName(service, deviceName);
		fprintf(stderr, "rDA: %s\n", deviceName);
		CFTypeRef str_bsd_path = IORegistryEntryCreateCFProperty(service, CFSTR(kIOBSDNameKey), kCFAllocatorDefault, 0);

		if (str_bsd_path != NULL) {
			int len = CFStringGetLength(str_bsd_path) * 2 + 1;
			char s[len];
			CFStringGetCString((CFStringRef) str_bsd_path, s, len, kCFStringEncodingUTF8);

			struct statfs *fs;
			fs = getFileSystemStatusDevMount(s);
			fprintf(stderr, "rDA BSD %s; %p\n", s, fs);
			if (fs) {
				StatfsObject *fso = [StatfsObject new];
				fso->fs = fs;
				[map setObject:fso forKey:(NSString *)str_bsd_path];
				
				notify(fs->f_mntonname, service, fs, true);
#ifdef IONOTIFYDISMOUNT
				io_object_t obj;
				kr = IOServiceAddInterestNotification(gNotifyPort, // notifyPort
						service, // service
						kIOGeneralInterest, // interestType
						DeviceNotification, // callback
						&obj, // refCon
						&obj // notification
						);
#endif
			} else if (!useNSWorkspace) {
				NSDate *fireDate = [NSDate dateWithTimeIntervalSinceNow:8.0];
				NSTimer *timer = [[NSTimer alloc] initWithFireDate:fireDate
					interval:0
					target:self 
					selector:@selector(handleTimer:) 
					userInfo:(void *)str_bsd_path repeats:NO];
				[[NSRunLoop currentRunLoop] addTimer:timer forMode: NSDefaultRunLoopMode];
			}

		}
		kr = IOObjectRelease(service);
	}

	fprintf(stderr, "rDA done\n");
}

-(void)mount:(id)notification
{
	NSString *path = [[notification userInfo] valueForKey:@"NSDevicePath"];

	// With the path, we can use statfs to get the device name (/mnt/<name>)
	// from device name, we can query UIServiceGetMatchingService and get info (like if it's optical media)

	const char *cPath = [path UTF8String];
	fprintf(stderr, "mount %s\n", cPath);
	struct statfs *fs = getFileSystemStatusFromMount(cPath);
	if (fs) {
		CFMutableDictionaryRef matchingDict;

		matchingDict = IOServiceMatching(kIOMediaClass);
		if (matchingDict == NULL) {
			fprintf(stderr, "IOServiceMatching returned NULL.\n");
			return;
		}

		io_service_t service = 0;
		char *sBSDName = strrchr(fs->f_mntfromname, (int) '/');
		if (sBSDName) {
			sBSDName++;

			fprintf(stderr, "Searching for %s\n", sBSDName);
			CFStringRef bsdname = CFStringCreateWithCString(kCFAllocatorDefault, sBSDName, kCFStringEncodingMacRoman);

			CFDictionarySetValue(matchingDict, CFSTR(kIOBSDNameKey), bsdname);
			service = IOServiceGetMatchingService(kIOMasterPortDefault, matchingDict);
		}

		notify(cPath, service, fs, true);

		if (service) {
#ifdef IONOTIFYDISMOUNT
			io_object_t obj;
			kern_return_t kr = IOServiceAddInterestNotification(gNotifyPort, // notifyPort
					service, // service
					kIOGeneralInterest, // interestType
					DeviceNotification, // callback
					&obj, // refCon
					&obj // notification
			);
#endif
			IOObjectRelease(service);
		}
	}
}

-(void)unmount:(id)notification
{
	NSString *path = [[notification userInfo] valueForKey:@"NSDevicePath"];

	// With the path, we can use statfs to get the device name (/mnt/<name>)
	// from device name, we can query UIServiceGetMatchingService and get info (like if it's optical media)
	
	const char *cPath = [path UTF8String];
	fprintf(stderr, "unmount %s\n", cPath);
	struct statfs *fs = getFileSystemStatusFromMount(cPath);
	io_service_t service = 0;

	// Alas, fs will always be null, so service lookup will never run
	// TODO: If we stored the NSDevicePath : fs->f_mntfromname mapping, and looked
	// it up, we might have a chance to get the ioservice..
	if (fs) {
		CFMutableDictionaryRef matchingDict;

		matchingDict = IOServiceMatching(kIOMediaClass);
		if (matchingDict == NULL) {
			fprintf(stderr, "IOServiceMatching returned NULL.\n");
			return;
		}

		char *sBSDName = strrchr(fs->f_mntfromname, (int) '/');
		if (sBSDName) {
			sBSDName++;

			CFStringRef bsdname = CFStringCreateWithCString(kCFAllocatorDefault, sBSDName, kCFStringEncodingMacRoman);

			CFDictionarySetValue(matchingDict, CFSTR(kIOBSDNameKey), bsdname);
			service = IOServiceGetMatchingService(kIOMasterPortDefault, matchingDict);
			//fprintf(stderr, "Searching for %s, result = %p\n", sBSDName, service);
		}
	}

	
	notify(cPath, service, fs, false);
	if (service) {
		IOObjectRelease(service);
	}
}

- (void)handleGetURLEvent:(NSAppleEventDescriptor *)event withReplyEvent:(NSAppleEventDescriptor *)replyEvent
{
	fprintf(stderr, "handleGetURL!\n");
	NSAppleEventDescriptor *desc = [event paramDescriptorForKeyword:keyDirectObject];
	NSString *urlstring = [desc stringValue];
	
	const char *cUrl = [urlstring UTF8String];
	fprintf(stderr, "handleGetURL! %s\n", cUrl);
	notifyURL(cUrl);
}

- (void)handleGetURLEvent2:(NSAppleEventDescriptor *)event withReplyEvent:(NSAppleEventDescriptor *)replyEvent
{
	fprintf(stderr, "handleGetURL2!\n");
	NSAppleEventDescriptor *desc = [event paramDescriptorForKeyword:keyDirectObject];
	NSString *urlstring = [desc stringValue];
	
	const char *cUrl = [urlstring UTF8String];
	fprintf(stderr, "handleGetURL2! %s\n", cUrl);
	notifyURL2(cUrl);
}

@end

@implementation StatfsObject
@end