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

#include "framework.h"
#include <windows.h>
#include <winioctl.h>
#include <stdio.h>
#include <	Cfgmgr32.h >
#include "aereg.h"

#include "com_biglybt_platform_win32_access_impl_AEWin32AccessInterface.h"

BOOL GetDriveGeometry(HANDLE hDevice, DISK_GEOMETRY *pdg)
{
	BOOL bResult;                 // results flag
	DWORD junk;                   // discard results

	bResult = DeviceIoControl(hDevice,  // device to be queried
		IOCTL_DISK_GET_DRIVE_GEOMETRY,  // operation to perform
		NULL, 0, // no input buffer
		pdg, sizeof(*pdg),     // output buffer
		&junk,                 // # bytes returned
		(LPOVERLAPPED) NULL);  // synchronous I/O

	return (bResult);
}


BOOL GetStorageProperty(HANDLE hDevice, PSTORAGE_DEVICE_DESCRIPTOR *p)
{
	DWORD junk;                   // discard results

	STORAGE_PROPERTY_QUERY Query;   // input param for query

	// specify the query type
	Query.PropertyId = StorageDeviceProperty;
	Query.QueryType = PropertyStandardQuery;


	BOOL res = DeviceIoControl(hDevice,                     // device handle
		IOCTL_STORAGE_QUERY_PROPERTY,             // info of device property
		&Query, sizeof(STORAGE_PROPERTY_QUERY),  // input data buffer
		*p, (*p)->Size,               // output data buffer
		&junk,                           // out's length
		(LPOVERLAPPED)NULL);

	return (res);
}

BOOL GetStorageDeviceID(HANDLE hDevice, PSTORAGE_DEVICE_ID_DESCRIPTOR *p)
{
	DWORD junk;                   // discard results

	STORAGE_PROPERTY_QUERY Query;   // input param for query

	// specify the query type
	Query.PropertyId = StorageDeviceIdProperty;
	Query.QueryType = PropertyStandardQuery;


	BOOL res = DeviceIoControl(hDevice,                     // device handle
		IOCTL_STORAGE_QUERY_PROPERTY,             // info of device property
		&Query, sizeof(STORAGE_PROPERTY_QUERY),  // input data buffer
		*p, (*p)->Size,               // output data buffer
		&junk,                           // out's length
		(LPOVERLAPPED)NULL);

	return (res);
}


JNIEXPORT jobject JNICALL Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getAvailableDrives
(JNIEnv *env, jclass cla)
{
	BOOL bResult;                 // generic results flag
	ULONGLONG DiskSize;           // size of the drive, in bytes

	HANDLE hDevice;

	// create List
	jclass clsArrayList = env->FindClass("java/util/ArrayList");
	jmethodID constArrayList = env->GetMethodID(clsArrayList, "<init>", "()V");
	jobject arrayList = env->NewObject(clsArrayList, constArrayList, "");
	jmethodID methAdd = env->GetMethodID(clsArrayList, "add", "(Ljava/lang/Object;)Z");


	// each bit returned is one drive, starting with "A:"
	DWORD dwLogicalDrives = GetLogicalDrives();

	for ( int nDrive = 0; nDrive<32; nDrive++ )
	{
		if ( (dwLogicalDrives & (1 << nDrive)) == 0 ) {
			continue;
		}

		char drive2[4];
		wsprintfA(drive2, "%C:\\", 'a' + nDrive);
		DWORD uType = GetDriveTypeA(drive2);

		// CreateFileW on Windows 7 on a Remote Drive that isn't attached will crash this call and cause parent .exe to be unkillable
		if (uType == DRIVE_REMOTE) {
			continue;
		}

		// Do an additional check by using GetDriveGeometry.  If it fails, then there's
		// no "disk" in the drive

		WCHAR drive[100];

		wsprintfW(drive, L"\\\\.\\%C:", 'a' + nDrive);
		hDevice = CreateFileW((LPCWSTR) drive,  // drive to open
			0,                // no access to the drive
			FILE_SHARE_READ | // share mode
			FILE_SHARE_WRITE,
			NULL,             // default security attributes
			OPEN_EXISTING,    // disposition
			0,                // file attributes
			NULL);            // do not copy file attributes

		if (hDevice == INVALID_HANDLE_VALUE) // cannot open the drive
		{
			continue;
		}

		CloseHandle(hDevice);

		// Create File
		jclass cls = env->FindClass("java/io/File");
		jmethodID constructor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;)V");
		jobject object = env->NewObject(cls, constructor, env->NewStringUTF(drive2));

		// add to list

		env->CallBooleanMethod( arrayList, methAdd, object );
	}
	return arrayList;
}

void addToMap(JNIEnv *env, jobject hashMap, jmethodID methPut, jclass clsLong, jmethodID longInit, const char *key, jlong val) {
	jobject longObj = env->NewObject(clsLong, longInit, val);
	env->CallObjectMethod(hashMap, methPut, env->NewStringUTF(key), longObj);
}

void addToMap(JNIEnv *env, jobject hashMap, jmethodID methPut, const char *key, char *val) {
	env->CallObjectMethod(hashMap, methPut, env->NewStringUTF(key), env->NewStringUTF(val));
}


void addToMap(JNIEnv *env, jobject hashMap, jmethodID methPut, const char *key, WCHAR *val, int val_len) {
	//int len = WideCharToMultiByte(CP_UTF8, 0, val, -1, NULL, 0, NULL, NULL);
	//char *utf8 = new char[len];
	//WideCharToMultiByte(CP_UTF8, 0, val, -1, utf8, len, NULL, NULL);
	//int val_len = wcslen(val);
	//addToMap(env, hashMap, methPut, key, utf8);
	env->CallObjectMethod(hashMap, methPut, env->NewStringUTF(key), env->NewString((jchar *)val, val_len));
	//delete[] utf8;
}

void findVID_PID(WCHAR *str, JNIEnv *env, jobject hashMap, jmethodID methPut) {
	WCHAR *vid = wcsstr(str, L"VID_");
	WCHAR *pid = wcsstr(str, L"PID_");
	if (vid) {
		vid += 4;
		addToMap(env, hashMap, methPut, "VID", vid, 4);
	}
	if (pid) {
		pid += 4;
		addToMap(env, hashMap, methPut, "PID", pid, 4);
	}
}

void CMStuff(char *devID, JNIEnv *env, jobject hashMap, jmethodID methPut)
{
    DEVINST devinst;
    DEVINST devinstparent;
    unsigned long buflen;

	CONFIGRET ret;

    ret = CM_Locate_DevNodeA(&devinst, devID, NULL);
	if (ret != CR_SUCCESS) {
		return;
	}
    ret = CM_Get_Parent(&devinstparent, devinst, NULL);
	if (ret != CR_SUCCESS) {
		return;
	}

	char *has = strstr(devID, "RemovableMedia");
	if (has) {
        CM_Get_Parent(&devinstparent, devinstparent, NULL);
		// if failed, devinstparent should still have previous value (I hope!)
	}

	ret = CM_Get_Device_ID_Size(&buflen, devinst, 0);
	if (ret != CR_SUCCESS) {
		return;
	}

	if (buflen < 2048) {
		buflen++; // add space for null, which CM_Get_Device_ID will add at end
		WCHAR *buffer = new WCHAR[buflen];
		ret = CM_Get_Device_ID(devinst, buffer, buflen, 0);
		if (ret != CR_SUCCESS) {
			delete[] buffer;
			return;
		}

		addToMap(env, hashMap, methPut, "DevInst_DevID", buffer, buflen);
		findVID_PID(buffer, env, hashMap, methPut);
		delete[] buffer;
	}

    ret = CM_Get_Device_ID_Size(&buflen, devinstparent, 0);
	if (ret != CR_SUCCESS) {
		return;
	}
	if (buflen < 2048) {
		buflen++; // add space for null, which CM_Get_Device_ID will add at end
		WCHAR *buffer = new WCHAR[buflen];
		ret = CM_Get_Device_ID(devinstparent, buffer, buflen, 0);
		if (ret != CR_SUCCESS) {
			delete[] buffer;
			return;
		}

		addToMap(env, hashMap, methPut, "DevInstParent_DevID", buffer, buflen);
		findVID_PID(buffer, env, hashMap, methPut);
		delete[] buffer;
	}
}


JNIEXPORT jobject JNICALL Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getDriveInfo
(JNIEnv *env, jclass cla, jchar driveLetter)
{
	jclass clsHashMap = env->FindClass("java/util/HashMap");
	jmethodID constHashMap = env->GetMethodID(clsHashMap, "<init>", "()V");
	jobject hashMap = env->NewObject(clsHashMap, constHashMap, "");
	jmethodID methPut = env->GetMethodID(clsHashMap, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

	jclass clsLong = env->FindClass("java/lang/Long");
	jmethodID longInit = env->GetMethodID(clsLong, "<init>", "(J)V");

	DISK_GEOMETRY pdg;            // disk drive geometry structure
	BOOL bResult;                 // generic results flag
	ULONGLONG DiskSize;           // size of the drive, in bytes


	char drive2[4];
	wsprintfA(drive2, "%C:\\", driveLetter);
	DWORD uType = GetDriveTypeA(drive2);

	// CreateFileW on Windows 7 on a Remote Drive that isn't attached will crash this call and cause parent .exe to be unkillable
	if (uType == DRIVE_REMOTE) {
		return hashMap;
	}


	HANDLE hDevice;
	WCHAR drive[100];

	wsprintfW(drive, L"\\\\.\\%C:", driveLetter);
	hDevice = CreateFileW((LPCWSTR) drive,  // drive to open
		0,                // no access to the drive
		FILE_SHARE_READ | // share mode
		FILE_SHARE_WRITE, 
		NULL,             // default security attributes
		OPEN_EXISTING,    // disposition
		0,                // file attributes
		NULL);            // do not copy file attributes

	addToMap(env, hashMap, methPut, clsLong, longInit, "DriveType", (jlong) uType);

	if (hDevice == INVALID_HANDLE_VALUE) // cannot open the drive
	{
		return hashMap;
	}


	bResult = GetDriveGeometry (hDevice, &pdg);

	if (bResult) 
	{
		LONGLONG diskSize = pdg.Cylinders.QuadPart * pdg.TracksPerCylinder *
			pdg.SectorsPerTrack * pdg.BytesPerSector;
		addToMap(env, hashMap, methPut, clsLong, longInit, "MediaType", (jlong) pdg.MediaType);
		addToMap(env, hashMap, methPut, clsLong, longInit, "DiskSize", (jlong) diskSize);
	}

	char OutBuf[1024] = {0};  // good enough, usually about 100 bytes
	PSTORAGE_DEVICE_DESCRIPTOR pDevDesc = (PSTORAGE_DEVICE_DESCRIPTOR)OutBuf;
	pDevDesc->Size = sizeof(OutBuf);

	bResult = GetStorageProperty(hDevice, &pDevDesc);

	if (bResult) {
		addToMap(env, hashMap, methPut, clsLong, longInit, "BusType", (jlong) pDevDesc->BusType);
		addToMap(env, hashMap, methPut, clsLong, longInit, "DeviceType", (jlong) pDevDesc->DeviceType);
		addToMap(env, hashMap, methPut, clsLong, longInit, "Removable", (jlong) pDevDesc->RemovableMedia);

		if (pDevDesc->VendorIdOffset > 0 && pDevDesc->VendorIdOffset < pDevDesc->Size) {
			addToMap(env, hashMap, methPut, "VendorID", &OutBuf[pDevDesc->VendorIdOffset]);
		}
		if (pDevDesc->ProductIdOffset > 0 && pDevDesc->ProductIdOffset < pDevDesc->Size) {
			addToMap(env, hashMap, methPut, "ProductID", &OutBuf[pDevDesc->ProductIdOffset]);
		}
		if (pDevDesc->ProductRevisionOffset > 0 && pDevDesc->ProductRevisionOffset < pDevDesc->Size) {
			addToMap(env, hashMap, methPut, "ProductRevision", &OutBuf[pDevDesc->ProductRevisionOffset]);
		}
		if (pDevDesc->SerialNumberOffset > 0 && pDevDesc->SerialNumberOffset < pDevDesc->Size) {
			addToMap(env, hashMap, methPut, "SerialNumber", &OutBuf[pDevDesc->SerialNumberOffset]);
		}
	}

	    STORAGE_DEVICE_NUMBER Strage_Device_Number;
    DWORD BytesReturned;

	        BOOL bResult6 = DeviceIoControl(
                     hDevice,                // handle to a partition
                     IOCTL_STORAGE_GET_DEVICE_NUMBER,   // dwIoControlCode
                     NULL,                            // lpInBuffer
                     0,                               // nInBufferSize
                     &Strage_Device_Number,            // output buffer
                     sizeof Strage_Device_Number,  // size of output buffer
                     &BytesReturned,       // number of bytes returned
                     NULL      // OVERLAPPED structure
                   );
	if (bResult6) {
		addToMap(env, hashMap, methPut, clsLong, longInit, "DeviceNumber", (jlong) Strage_Device_Number.DeviceNumber);
	}

	
	char subkey[14];
	wsprintfA(subkey, "\\DosDevices\\%C:", driveLetter);

	DWORD valuesize;
    HKEY key;
    int res;

    subkey[12] = driveLetter;
    res = RegOpenKeyExA( HKEY_LOCAL_MACHINE, "SYSTEM\\MountedDevices", NULL, KEY_QUERY_VALUE, &key);
	
	if(RegQueryValueExA(key, subkey, NULL, NULL, NULL, &valuesize) == ERROR_SUCCESS && valuesize > 8) {
		WCHAR *value = new WCHAR[valuesize / 2];
		res = RegQueryValueExA(key, subkey, NULL, NULL,(LPBYTE) value, &valuesize);
		valuesize /= 2;
        if(res == ERROR_SUCCESS)
        {
			char *devname = new char[valuesize];
			int pos = 0;
			for (int i = 4; i < valuesize; i ++) {
				char c = value[i];
				if (c == '{') {
					if (devname[pos - 1] == '\\') {
						devname[pos - 1] = 0;
					}
					break;
				}

				if (c == '#') {
					c = '\\';
				}

				devname[pos] = c;
				pos++;
			}

			if (devname[0] != 0) {
				devname[pos++] = 0;
				devname[pos++] = 0;

				addToMap(env, hashMap, methPut, "OSDeviceID", devname);


				CMStuff(devname, env, hashMap, methPut);
			}
			delete[] devname;
        }
        delete[] value;
	}
	RegCloseKey(key);


	CloseHandle(hDevice);
	return hashMap;
}


