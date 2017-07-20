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

#include <Carbon/Carbon.h>
#include <JavaVM/jni.h>
#ifdef CARBON
#include <AEDataModel.h>
#endif
#include "com_biglybt_platform_macosx_access_jnilib_OSXAccess.h"
#include <IOKit/IOBSD.h>
#include <sys/mount.h>
#include <wchar.h>
#import <IOKit/storage/IOMedia.h>
#import <IOKit/storage/IOCDMedia.h>
#import <IOKit/storage/IODVDMedia.h>
#include <IOKit/storage/IOBlockStorageDevice.h>
#include <IOKit/usb/USBSpec.h>

#include "IONotification.h"
#ifndef CARBON
#include "LaunchServicesWrapper.h"
#endif

#define VERSION "1.12"

#define assertNot0(a) if (a == 0) { fprintf(stderr, "%s is 0\n", #a); return; }
void fillServiceInfo(io_service_t service, JNIEnv *env, jobject hashMap, jmethodID methPut);

extern "C" {
	void notify(const char *mount, io_service_t service, struct statfs *fs, bool added);
	void notifyURL(const char *url);
	void notifyURL2(const char *url);
}

/**
* AEDesc code from SWT, os_structs.c
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 */
typedef struct AEDesc_FID_CACHE {
	int cached;
	jclass clazz;
	jfieldID descriptorType, dataHandle;
} AEDesc_FID_CACHE;

AEDesc_FID_CACHE AEDescFc;

static jclass gCallBackClass = 0;
static jobject gCallBackObj = 0;

static JavaVM *gjvm = 0;

static int indent = 0;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	gjvm = vm;

	return JNI_VERSION_1_4;
}

#ifdef CARBON

void cacheAEDescFields(JNIEnv *env, jobject lpObject) {
	if (AEDescFc.cached)
		return;
	AEDescFc.clazz = env->GetObjectClass(lpObject);
	AEDescFc.descriptorType = env->GetFieldID(AEDescFc.clazz, "descriptorType", "I");
	AEDescFc.dataHandle = env->GetFieldID(AEDescFc.clazz, "dataHandle", "I");
	AEDescFc.cached = 1;
}

AEDesc *getAEDescFields(JNIEnv *env, jobject lpObject, AEDesc *lpStruct) {
	if (!AEDescFc.cached)
		cacheAEDescFields(env, lpObject);
	lpStruct->descriptorType = (DescType) env->GetIntField(lpObject, AEDescFc.descriptorType);
	lpStruct->dataHandle = (AEDataStorage) env->GetIntField(lpObject, AEDescFc.dataHandle);
	return lpStruct;
}

void setAEDescFields(JNIEnv *env, jobject lpObject, AEDesc *lpStruct) {
	if (!AEDescFc.cached)
		cacheAEDescFields(env, lpObject);
	env->SetIntField(lpObject, AEDescFc.descriptorType, (jint) lpStruct->descriptorType);
#ifndef __LP64__
	env->SetIntField(lpObject, AEDescFc.dataHandle, (jint) lpStruct->dataHandle);
#endif
}

JNIEXPORT jint JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_AEGetParamDesc(JNIEnv *env,
																																																		 jclass that, jint theAppleEvent, jint theAEKeyword, jint desiredType, jobject result) {
	AEDesc _result, *lpresult = NULL;
	
	jint rc = 0;
	
	if (result)
		if ((lpresult = getAEDescFields(env, result, &_result)) == NULL)
			goto fail;
	
	rc = (jint) AEGetParamDesc((const AppleEvent *) theAppleEvent, (AEKeyword) theAEKeyword, (DescType) desiredType,
														 (AEDescList *) lpresult);
	
fail: if (result && lpresult)
		setAEDescFields(env, result, lpresult);
	
	return rc;
}
#endif

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_getVersion(
																																					JNIEnv *env, jclass cla) {
	jstring result = env->NewStringUTF((char *) VERSION);
	
	return (result);
}

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_getDocDir(
																																				 JNIEnv *env, jclass cla) {
	CFURLRef docURL;
	CFStringRef docPath;
	FSRef fsRef;
	OSErr err = FSFindFolder(kUserDomain, kDocumentsFolderType,
													 kDontCreateFolder, &fsRef);
	
	jstring result = 0;
	
	if (err == noErr) {
		if ((docURL = CFURLCreateFromFSRef(kCFAllocatorSystemDefault, &fsRef))) {
			docPath = CFURLCopyFileSystemPath(docURL, kCFURLPOSIXPathStyle);
			
			if (docPath) {
				// convert to unicode
				CFIndex strLen = CFStringGetLength(docPath);
				UniChar uniStr[strLen];
				CFRange strRange;
				strRange.location = 0;
				strRange.length = strLen;
				CFStringGetCharacters(docPath, strRange, uniStr);
				
				result = env->NewString((jchar*) uniStr, (jsize) strLen);
				
				CFRelease(docPath);
				
				return result;
			}
			CFRelease(docURL);
		}
	}
	return result;
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_memmove(
																																			 JNIEnv *env, jclass cla, jbyteArray dest, jint src, jint count) {
	jbyte *dest1;
	
	if (dest) {
		dest1 = env->GetByteArrayElements(dest, NULL);
		memmove((void *) dest1, (void *) src, count);
		env->ReleaseByteArrayElements(dest, dest1, 0);
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_initializeDriveDetection
(JNIEnv *env, jclass cla, jobject listener)
{
	// OSXDriveDetectListener
	jclass callback_class = env->GetObjectClass(listener);
	gCallBackClass = (jclass) env->NewGlobalRef(callback_class);
	gCallBackObj = (jobject) env->NewGlobalRef(listener);
	
	IONotification *mountNotification = [IONotification alloc];
	[mountNotification setup];
}

/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    initializeLight
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_initializeLight
(JNIEnv *env, jclass cla) {
	IONotification *mountNotification = [IONotification alloc];
	[mountNotification setupLight];
}


NSString *jstring2nsstring (JNIEnv *env, jstring jstr)
{
    char *cStr;
    NSString *nsString;
    
    cStr = (char *)env->GetStringUTFChars(jstr, NULL);
    if (!cStr) {
        return NULL;
    }
    
    // stringWithCString makes a copy of cStr
    nsString = [[NSString class] stringWithCString: cStr];
    
    env->ReleaseStringUTFChars (jstr, cStr);
    
    return nsString;
}


jstring wchar2jstring(JNIEnv* env, const wchar_t* s) {
	jstring result = 0;
	size_t len = wcslen(s);
	size_t sz = wcstombs(0, s, len);
	char c[sz + 1];
	wcstombs(c, s, len);
	c[sz] = '\0';
	result = env->NewStringUTF(c);
	return result;
}

jstring char2jstring(JNIEnv* env, const char *str) {
	return (jstring) env->NewStringUTF(str);
}

jstring CFString2jstring(JNIEnv *env, CFStringRef cfstr) {
	int len = CFStringGetLength(cfstr) * 2 + 1;
	char s[len];
	CFStringGetCString(cfstr, s, len, kCFStringEncodingUTF8);
	return env->NewStringUTF(s);
}

jstring NSString2jstring(JNIEnv *env, NSString *s) {
	if (s == NULL) {
		return 0;
	}
	const char *c = [s UTF8String];
	return env->NewStringUTF(c);
}

jobject createLong(JNIEnv *env, jlong l) {
	jclass clsLong = env->FindClass("java/lang/Long");
	jmethodID longInit = env->GetMethodID(clsLong, "<init>", "(J)V");
	
	jobject o = env->NewObject(clsLong, longInit, l);
	return o;
}

#define IOOBJECTRELEASE(x) if ((x)) IOObjectRelease((x)); (x) = NULL;

io_object_t IOKitObjectFindParentOfClass(io_object_t inService, const io_name_t inClassName) {
	io_object_t rval = NULL;
	io_iterator_t iter = NULL;
	io_object_t service = NULL;
	kern_return_t kr;
	
	if (!inService || !inClassName) {
		return NULL;
	}
	
	kr = IORegistryEntryCreateIterator(inService, kIOServicePlane, kIORegistryIterateRecursively
																		 | kIORegistryIterateParents, &iter);
	if (kr != KERN_SUCCESS) {
		goto IORegistryEntryCreateIterator_FAILED;
	}
	
	if (!IOIteratorIsValid(iter)) {
		IOIteratorReset(iter);
	}
	
	while ((service = IOIteratorNext(iter))) {
		if (IOObjectConformsTo(service, inClassName)) {
			rval = service;
			break;
		}
		
		IOOBJECTRELEASE(service);
	}
	
	IOOBJECTRELEASE(iter);
	
IORegistryEntryCreateIterator_FAILED: return rval;
}

void notify(const char *mount, io_service_t service, struct statfs *fs, bool added) {
	
	assertNot0(gCallBackClass);
	assertNot0(gjvm);
	
	JNIEnv* env = NULL;
	gjvm->AttachCurrentThread((void **) &env, NULL);
	assertNot0(env);
	
	jmethodID meth;
	if (added) {
		meth = env->GetMethodID(gCallBackClass, "driveDetected", "(Ljava/io/File;Ljava/util/Map;)V");
	} else {
		meth = env->GetMethodID(gCallBackClass, "driveRemoved", "(Ljava/io/File;Ljava/util/Map;)V");
	}
	
	assertNot0(meth);
	
	jclass clsHashMap = env->FindClass("java/util/HashMap");
	assertNot0(clsHashMap);
	jmethodID constHashMap = env->GetMethodID(clsHashMap, "<init>", "()V");
	assertNot0(constHashMap);
	jmethodID methPut = env->GetMethodID(clsHashMap, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
	assertNot0(methPut);
	
	jclass clsFile = env->FindClass("java/io/File");
	assertNot0(clsFile);
	jmethodID constFile = env->GetMethodID(clsFile, "<init>", "(Ljava/lang/String;)V");
	assertNot0(constFile);
	
	jobject file = 0;
	if (mount) {
		file = env->NewObject(clsFile, constFile, char2jstring(env, mount));
		assertNot0(file);
	}
	
	jobject hashMap = env->NewObject(clsHashMap, constHashMap, "");
	assertNot0(hashMap);
	
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	
	if (fs) {
		/**
		NSString *path = [[NSString alloc] initWithUTF8String:fs->f_mntonname];
		 NSWorkspace *ws = [NSWorkspace sharedWorkspace];
		 BOOL removable;
		 BOOL writable;
		 BOOL unmountable;
		 NSString *description;
		 NSString *fileSystemType;
		 BOOL gotInfo = [ws getFileSystemInfoForPath:path isRemovable:&removable isWritable:&writable isUnmountable:&unmountable description:&description type:&fileSystemType];
		 if (gotInfo) {
			 if (description) {
				 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "description"), NSString2jstring(env, description));
			 }
			 if (fileSystemType) {
				 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "fileSystemType"), NSString2jstring(env,
																																																			 fileSystemType));
			 }
			 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "removable"), createLong(env, (jlong) removable));
			 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "writable"), createLong(env, (jlong) writable));
			 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "unmountable"), createLong(env, (jlong) unmountable));
		 }
		 
		 [path release];
		 **/
		
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "mntfromname"), char2jstring(env, fs->f_mntfromname));
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "mntonname"), char2jstring(env, fs->f_mntonname));
	}

	if (service) {
		fillServiceInfo(service, env, hashMap, methPut);
	}
	
	env->CallVoidMethod(gCallBackObj, meth, file, hashMap);

	[pool release];
}

void putCFNumberIntoHashMap(const char *key, const char *hexkey, CFNumberRef cft, JNIEnv *env, jobject hashMap, jmethodID methPut) {
	@try {
	if (cft == nil || !cft) {
		return;
	}
	long long n;
	double d;
	CFNumberRef cfr = (CFNumberRef)cft;
	CFNumberType cfnt = CFNumberGetType(cfr);
	switch (cfnt) {
		case kCFNumberSInt8Type:
		case kCFNumberSInt16Type:
		case kCFNumberSInt32Type:
		case kCFNumberSInt64Type:
		case kCFNumberCharType:
		case kCFNumberShortType:
		case kCFNumberIntType:
		case kCFNumberLongType:
		case kCFNumberLongLongType:
			CFNumberGetValue(cfr, kCFNumberLongLongType, &n);
			fprintf(stderr, " = %lld", n);
			if (key) {
				env->CallObjectMethod(hashMap, methPut, char2jstring(env, key), createLong(env, (jlong) n));
			}
			if (hexkey) {
				char cHex[50];
				sprintf(cHex, "%04llX", n);
				env->CallObjectMethod(hashMap, methPut, char2jstring(env, hexkey), char2jstring(env, cHex));
			}
			break;
		case kCFNumberFloat32Type:
		case kCFNumberFloat64Type:
		case kCFNumberFloatType:
		case kCFNumberDoubleType:
			CFNumberGetValue(cfr, kCFNumberDoubleType, &d);
			fprintf(stderr, " = %f", d);
			break;
		default:
			fprintf(stderr, " = UNKNOWN TYPE %d", (int) cfnt);
			break;
	}
	}
	@catch (NSException * e) {
		fprintf(stderr, "NSException %s (%s)", [[e name] UTF8String], [[e reason] UTF8String]);
	}
	@finally {
	}
}

void addDictionaryToHashMap(CFDictionaryRef dict, JNIEnv *env, jobject hashMap, jmethodID methPut) {
	CFIndex		count;
	CFIndex 		i;
	const void * *	keys;
	boolean_t		new_is_linklocal = TRUE;
	CFStringRef		new_primary = NULL;
	unsigned int 	primary_index = 0;
	const void * *	values;
	
	count = CFDictionaryGetCount(dict);
	if (count == 0) {
		return;
	}
	
	keys   = (const void * *)malloc(sizeof(void *) * count);
	values = (const void * *)malloc(sizeof(void *) * count);
	
	if (keys == NULL || values == NULL) {
		return;
	}
	
	CFDictionaryGetKeysAndValues(dict, keys, values);
	
	for (i = 0; i < count; i++) {
		
		for (int j = 0; j < indent; j++) {
			fprintf(stderr, "\t");
		}
		fprintf(stderr, "%d. ", (int) i);
		CFStringRef cfstr = (CFStringRef) keys[i];
		int len = CFStringGetLength(cfstr) * 2 + 1;
		char key[len];
		CFStringGetCString(cfstr, key, len, kCFStringEncodingUTF8);
		fprintf(stderr, "%s", key);
		
		CFTypeRef cft = (CFTypeRef) values[i];
		CFTypeID tCFTypeID = CFGetTypeID(cft);
		if (tCFTypeID == CFStringGetTypeID()) {
			CFStringRef cfstr = (CFStringRef) cft;
			
			int len = CFStringGetLength(cfstr) * 2 + 1;
			char s[len];
			CFStringGetCString(cfstr, s, len, kCFStringEncodingUTF8);
			fprintf(stderr, " = %s", s);
			env->CallObjectMethod(hashMap, methPut, char2jstring(env, key), char2jstring(env, s));
		} else if (tCFTypeID == CFBooleanGetTypeID()) {
			fprintf(stderr, " = %s", ((CFBooleanRef)cft == kCFBooleanTrue) ? "true" : "false");
			BOOL b = (CFBooleanRef)cft == kCFBooleanTrue;
			env->CallObjectMethod(hashMap, methPut, char2jstring(env, key), createLong(env, (jlong) b));
		} else if (tCFTypeID == CFNumberGetTypeID()) {
			putCFNumberIntoHashMap(key, NULL, (CFNumberRef) cft,env, hashMap, methPut);
		} else if (tCFTypeID == CFDictionaryGetTypeID()) {
			CFDictionaryRef        sub;
			sub = (CFDictionaryRef) CFDictionaryGetValue(dict, cfstr);
			if (sub) {
				indent++;
				fprintf(stderr, "Dictionary ->\n");
				addDictionaryToHashMap(sub, env, hashMap, methPut);
				indent--;
			}
		} else {
			fprintf(stderr, " unknown %d", (int) tCFTypeID);
		}
		fprintf(stderr, "\n"); fflush(stderr);
		}
		free(keys);
		free(values);
}

CFTypeRef FindProp( io_registry_entry_t e, CFStringRef key, bool up )
{
	IOOptionBits bits = kIORegistryIterateRecursively;
	
	if( up )
	{
		bits |= kIORegistryIterateParents;
	}
	return IORegistryEntrySearchCFProperty( e, kIOServicePlane, key, NULL, 
										   bits );
}

void fillServiceInfo(io_service_t service, JNIEnv *env, jobject hashMap, jmethodID methPut) {
	io_name_t deviceName;
	kern_return_t kr = IORegistryEntryGetName(service, deviceName);
	if (KERN_SUCCESS == kr) {
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "DeviceName"), char2jstring(env, deviceName));
	}
	
	CFStringRef str_bsd_path = (CFStringRef) IORegistryEntryCreateCFProperty(service, CFSTR(kIOBSDNameKey),
																																					 kCFAllocatorDefault, 0);
	if (str_bsd_path) {
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "BSDName"), CFString2jstring(env, str_bsd_path));
	}
	
	BOOL cd = IOObjectConformsTo(service, kIOCDMediaClass);
	BOOL dvd = IOObjectConformsTo(service, kIODVDMediaClass);
	
	io_service_t ioparent;
	ioparent = IOKitObjectFindParentOfClass(service, kIOMediaClass);
	if (ioparent) {
		jclass clsHashMap = env->FindClass("java/util/HashMap");
		assertNot0(clsHashMap);
		jmethodID constHashMap = env->GetMethodID(clsHashMap, "<init>", "()V");
		assertNot0(constHashMap);
		jobject parentHashMap = env->NewObject(clsHashMap, constHashMap, "");
		assertNot0(parentHashMap);
		
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "parent"), parentHashMap);
		fillServiceInfo(ioparent, env, parentHashMap, methPut);
		
		cd |= IOObjectConformsTo(ioparent, kIOCDMediaClass);
		dvd |= IOObjectConformsTo(ioparent, kIODVDMediaClass);
		
		IOOBJECTRELEASE(ioparent)
	}
	
	env->CallObjectMethod(hashMap, methPut, char2jstring(env, "isCD"), createLong(env, (jlong) cd));
	env->CallObjectMethod(hashMap, methPut, char2jstring(env, "isDVD"), createLong(env, (jlong) dvd));
	// we can expand this one later if needed
	env->CallObjectMethod(hashMap, methPut, char2jstring(env, "isOptical"), createLong(env, (jlong)(dvd || cd)));
	
	CFMutableDictionaryRef properties = NULL;
	kr = IORegistryEntryCreateCFProperties(service, &properties, kCFAllocatorDefault, 0);
	if (kr == KERN_SUCCESS) {
		fprintf(stderr, "service:\n");
		indent++;
		addDictionaryToHashMap(properties, env, hashMap, methPut);
		indent--;
		CFRelease(properties);
	}


	io_service_t device;
	io_iterator_t services;
	kr = IORegistryEntryCreateIterator(service, kIOServicePlane,
																			kIORegistryIterateParents | kIORegistryIterateRecursively,
																			&services);

	while ((device = IOIteratorNext(services)))
	{
			if (IOObjectConformsTo(device, kIOBlockStorageDeviceClass))  break;

			IOObjectRelease(device);
	}

	IOObjectRelease(services);

	if (device) {
		CFNumberRef vid = (CFNumberRef)FindProp(device, CFSTR(kUSBVendorID), true);
		if (vid) {
			putCFNumberIntoHashMap("Vendor ID", "VID", vid, env, hashMap, methPut);
		}
		CFNumberRef pid = (CFNumberRef)FindProp(device, CFSTR(kUSBProductID), true);
		if (pid) {
			putCFNumberIntoHashMap("Product ID", "PID", pid, env, hashMap, methPut);
		}

		kr = IORegistryEntryCreateCFProperties(device, &properties, kCFAllocatorDefault, 0);
		if (kr == KERN_SUCCESS) {
			fprintf(stderr, "device:\n");
			indent++;
			
			addDictionaryToHashMap(properties, env, hashMap, methPut);
			
			indent--;

			CFRelease(properties);
		}
		IOObjectRelease(device);
	}
	
}

void notifyURL2(const char *url) {
	if (url == NULL) {
		return;
	}
	assertNot0(gjvm);
	
	JNIEnv* env = NULL;
	gjvm->AttachCurrentThread((void **) &env, NULL);
	assertNot0(env);

    jclass clsOSXAccess = env->FindClass("com/biglybt/platform/macosx/access/jnilib/OSXAccess");
    if (clsOSXAccess) {
        
        fprintf(stderr, "has OSXAccess for %s\n", url);
        
        jmethodID meth = env->GetStaticMethodID(clsOSXAccess, "passParameter", "(Ljava/lang/String;)V");
        if (meth) {
            jstring str = (jstring) env->NewStringUTF(url);
            
            env->CallStaticVoidMethod(clsOSXAccess, meth, str);
            
            fprintf(stderr, "passParameter called for %s\n", url);
            return;
        }
    }
    
}

void notifyURL(const char *url) {
	if (url == NULL) {
		return;
	}
	assertNot0(gCallBackClass);
	assertNot0(gjvm);
	
	JNIEnv* env = NULL;
	gjvm->AttachCurrentThread((void **) &env, NULL);
	assertNot0(env);
    
    fprintf(stderr, "notifyURL Fallback for %s\n", url);
	jclass clsTorrentOpener = env->FindClass("com/biglybt/ui/swt/mainwindow/TorrentOpener");
	assertNot0(clsTorrentOpener);
	
	jmethodID methOpenTorrent = env->GetStaticMethodID(clsTorrentOpener, "openTorrent", "(Ljava/lang/String;)V");
	assertNot0(methOpenTorrent);
	
	jstring str = (jstring) env->NewStringUTF(url);
	
	env->CallStaticVoidMethod(clsTorrentOpener, methOpenTorrent, str);
}



/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    setDefaultAppForExt
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT jboolean JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_setDefaultAppForExt
(JNIEnv *env, jclass cla, jstring jbundleID, jstring jext)
{
#ifdef CARBON
    return (jboolean) 0;
#else
    NSString *bundleID = jstring2nsstring(env, jbundleID);
    if (bundleID == NULL) {
        return (jboolean) 0;
    }
    
    NSString *ext = jstring2nsstring(env, jext);
    if (ext == NULL) {
        return (jboolean) 0;
    }

    BOOL result = [LaunchServicesWrapper setDefaultApplication:bundleID forExtension:ext];
    fprintf(stderr, "bundleID %s Exit %s\n", [bundleID UTF8String], [ext UTF8String]);
    return (jboolean) result;
#endif
}

/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    setDefaultAppForMime
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT jboolean JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_setDefaultAppForMime
(JNIEnv *env, jclass cla, jstring jbundleID, jstring jmime)
{
#ifdef CARBON
    return (jboolean) 0;
#else
    NSString *bundleID = jstring2nsstring(env, jbundleID);
    if (bundleID == NULL) {
        return (jboolean) 0;
    }
    
    NSString *mime = jstring2nsstring(env, jmime);
    if (mime == NULL) {
        return (jboolean) 0;
    }
    BOOL result = [LaunchServicesWrapper setDefaultApplication:bundleID forMimeType:mime];
    fprintf(stderr, "bundleID %s Mime %s\n", [bundleID UTF8String], [mime UTF8String]);
    return (jboolean) result;
#endif
}

/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    setDefaultAppForScheme
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT jboolean JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_setDefaultAppForScheme
(JNIEnv *env, jclass cla, jstring jbundleID, jstring jscheme)
{
#ifdef CARBON
    return (jboolean) 0;
#else
    NSString *bundleID = jstring2nsstring(env, jbundleID);
    if (bundleID == NULL) {
        return (jboolean) 0;
    }
    
    NSString *scheme = jstring2nsstring(env, jscheme);
    if (scheme == NULL) {
        return (jboolean) 0;
    }
    BOOL result = [LaunchServicesWrapper setDefaultApplication:bundleID forScheme:scheme];
    
    fprintf(stderr, "App %s Scheme %s\n", [bundleID UTF8String], [scheme UTF8String]);
    return (jboolean) result;
#endif
}

/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    getDefaultAppForExt
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_getDefaultAppForExt
(JNIEnv *env, jclass cla, jstring jext)
{
#ifndef CARBON
    NSString *ext = jstring2nsstring(env, jext);
    if (ext != NULL) {
        NSString *def = [LaunchServicesWrapper defaultApplicationForExtension:ext];
        
        if (def) {
            return NSString2jstring(env, def);
        }
    }
#endif
    return (jstring) NULL;
}

/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    getDefaultAppForMime
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_getDefaultAppForMime
(JNIEnv *env, jclass cla, jstring jmime)
{
#ifndef CARBON
    NSString *mime = jstring2nsstring(env, jmime);
    if (mime != NULL) {
        NSString *def = [LaunchServicesWrapper defaultApplicationForMimeType:mime];

        if (def) {
            return NSString2jstring(env, def);
        }
    }
#endif
    return (jstring) NULL;
}

/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    getDefaultAppForScheme
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_getDefaultAppForScheme
(JNIEnv *env, jclass cla, jstring jscheme)
{
#ifndef CARBON
    NSString *scheme = jstring2nsstring(env, jscheme);
    if (scheme) {
        NSString *def = [LaunchServicesWrapper defaultApplicationForScheme:scheme];

        if (def) {
            return NSString2jstring(env, def);
        }
    }
#endif
    return (jstring) NULL;
}


/*
 * Class:     com_biglybt_platform_macosx_access_jnilib_OSXAccess
 * Method:    canSetDefaultApp
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_canSetDefaultApp
(JNIEnv *env, jclass cla)
{
#ifdef CARBON
    return (jboolean) 0;
#endif
    return (jboolean) 1;
}

