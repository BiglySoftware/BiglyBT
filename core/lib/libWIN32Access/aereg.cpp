/*
 * Created on Apr 16, 2004
 * Created by Paul Gardner
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
#include "aereg.h"
#include "stdio.h"
#include "stdlib.h"
#include "windows.h"
#include "shlwapi.h"
#include "process.h"
#include "shellapi.h"


#include "com_biglybt_platform_win32_access_impl_AEWin32AccessInterface.h"


#define VERSION "1.27"


HMODULE	application_module;
bool	non_unicode			= false;

jclass AEWin32AccessInterface_class;
jclass AEWin32AccessExceptionImpl_class;

UINT		uThreadId;
HINSTANCE	hInstance;
LRESULT CALLBACK WndProcW(HWND, UINT, WPARAM, LPARAM);
void	RegisterWindowClassW();

LRESULT CALLBACK WndProcA(HWND, UINT, WPARAM, LPARAM);
void	RegisterWindowClassA();

HRESULT
callback(UINT	Msg, WPARAM	wParam, LPARAM	lParam) ;

JavaVM*	jvm;

BOOL APIENTRY
DllMain(
	HINSTANCE	hModule,
    DWORD		ul_reason_for_call,
    LPVOID		lpReserved )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
		{
			hInstance	= hModule;

			application_module = (HMODULE)hModule;

			if ( non_unicode ){
				RegisterWindowClassA();
			}else{
				RegisterWindowClassW();
			}

			break;
		}
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
    }
    return TRUE;
}




CAereg::CAereg()
{
	return;
}


void
throwException(
	JNIEnv*			env,
	const char*		operation,
	const char*		message )
{
	bool	ok = false;

	if ( AEWin32AccessExceptionImpl_class != NULL ){

		jmethodID method = env->GetMethodID( AEWin32AccessExceptionImpl_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V" );

		if ( method != NULL ){


			jobject new_object =
					env->NewObject( AEWin32AccessExceptionImpl_class,
									method,
									env->NewStringUTF((const char*)operation),
									env->NewStringUTF((const char*)message ));

			if ( new_object != NULL ){

				env->Throw( (jthrowable)new_object );

				ok = true;
			}
		}else{

			fprintf( stderr, "AEWin32AccessInterface: failed to resolve constructor" );
		}
	}

	if ( !ok ){

		fprintf( stderr, "AEWin32AccessInterface: failed to throw exception %s: %s\n", operation, message );
	}
}

void
throwException(
	JNIEnv*			env,
	const char*		operation,
	const char*		message,
	int				error_code )
{
	const size_t buffer_size = 4096;
	char	buffer[buffer_size];

	sprintf_s( buffer, buffer_size, "%s (error_code=%d)", message, error_code );

	throwException( env, operation, buffer );
}

HKEY
mapHKEY(
	JNIEnv*		env,
	jint		_type )
{
	if ( _type == com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_HKEY_CLASSES_ROOT ){

		return( HKEY_CLASSES_ROOT );

	}else if ( _type == com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_HKEY_CURRENT_CONFIG ){

		return( HKEY_CURRENT_CONFIG );

	}else if ( _type == com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_HKEY_LOCAL_MACHINE ){

		return( HKEY_LOCAL_MACHINE );

	}else if ( _type == com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_HKEY_CURRENT_USER ){

		return( HKEY_CURRENT_USER );

	}else{

		throwException( env, "readValue", "unsupported type" );

		return( NULL );
	}
}


// ******************************

bool
jstringToCharsW(
	JNIEnv		*env,
	jstring		jstr,
	WCHAR		*chars,
	int			chars_len )
{
	if ( jstr == NULL ){

		chars[0] = 0;

	}else{

		int	jdata_len = env->GetStringLength(jstr);

		if ( jdata_len >= chars_len ){

			throwException( env, "jstringToChars", "jstring truncation occurred" );

			return( false );
		}

		const jchar *jdata = env->GetStringChars( jstr, NULL );

		for (int i=0;i<jdata_len;i++){

			chars[i] = (WCHAR)jdata[i];
		}

		chars[jdata_len]=0;

		env->ReleaseStringChars( jstr, jdata );
	}

	return( true );
}


// WINDOWS HOOK

void
RegisterWindowClassW()
{
	WNDCLASSEXW wcex;

	wcex.cbSize = sizeof(WNDCLASSEXW);
	wcex.style			= CS_HREDRAW | CS_VREDRAW;
	wcex.lpfnWndProc	= WndProcW;
	wcex.cbClsExtra		= 0;
	wcex.cbWndExtra		= 0;
	wcex.hInstance		= hInstance;
	wcex.hIcon			= 0;
	wcex.hCursor		= 0;
	wcex.hbrBackground	= (HBRUSH)(COLOR_WINDOW + 1);
	wcex.lpszMenuName	= 0;
	wcex.lpszClassName	= L"BiglyBT Window Hook";
	wcex.hIconSm		= 0;

	RegisterClassExW(&wcex);
}


LRESULT CALLBACK
WndProcW(
	HWND hWnd,
	UINT Msg,
	WPARAM wParam,
	LPARAM lParam)
{
	long res = callback( Msg, wParam, lParam );

	if ( res != -1 ){

		return( res );
	}

	return DefWindowProcW(hWnd, Msg, wParam, lParam);
}

unsigned WINAPI
CreateWndThreadW(
	LPVOID pThreadParam)
{
	HWND hWnd = CreateWindowW( L"BiglyBT Window Hook", NULL, WS_OVERLAPPEDWINDOW,
									CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT,
									NULL, NULL, hInstance, NULL);
	if( hWnd == NULL){

		printf( "Failed to create window\n" );

		return( 0 );

	}else{

		MSG Msg;

		while(GetMessageW(&Msg, hWnd, 0, 0)) {

			TranslateMessage(&Msg);

			DispatchMessageW(&Msg);
		}

		return Msg.wParam;
	}
}


//

void
RegisterWindowClassA()
{
	WNDCLASSEXA wcex;

	wcex.cbSize = sizeof(WNDCLASSEXA);
	wcex.style			= CS_HREDRAW | CS_VREDRAW;
	wcex.lpfnWndProc	= WndProcA;
	wcex.cbClsExtra		= 0;
	wcex.cbWndExtra		= 0;
	wcex.hInstance		= hInstance;
	wcex.hIcon			= 0;
	wcex.hCursor		= 0;
	wcex.hbrBackground	= (HBRUSH)(COLOR_WINDOW + 1);
	wcex.lpszMenuName	= 0;
	wcex.lpszClassName	= "BiglyBT Window Hook";
	wcex.hIconSm		= 0;

	RegisterClassExA(&wcex);
}


LRESULT CALLBACK
WndProcA(
	HWND hWnd,
	UINT Msg,
	WPARAM wParam,
	LPARAM lParam)
{
	long res = callback( Msg, wParam, lParam );

	if ( res != -1 ){

		return( res );
	}

	return DefWindowProcA(hWnd, Msg, wParam, lParam);
}

unsigned WINAPI
CreateWndThreadA(
	LPVOID pThreadParam)
{
	HWND hWnd = CreateWindowA( "BiglyBT Window Hook", NULL, WS_OVERLAPPEDWINDOW,
									CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT,
									NULL, NULL, hInstance, NULL);
	if( hWnd == NULL){

		printf( "Failed to create window\n" );

		return( 0 );

	}else{

		MSG Msg;

		while(GetMessageA(&Msg, hWnd, 0, 0)) {

			TranslateMessage(&Msg);

			DispatchMessageA(&Msg);
		}

		return Msg.wParam;
	}
}


HRESULT
callback(
	UINT	Msg,
	WPARAM	wParam,
	LPARAM	lParam)
{
	JNIEnv *env;

	if ( jvm->AttachCurrentThread((void **)&env, NULL )){

		fprintf( stderr, "failed to attach current thread to JVM\n" );

		return( -1 );
	}


	jlong result = -1;

	if ( AEWin32AccessInterface_class != NULL ){

		jint	j_msg		= Msg;
		jint	j_param1	= wParam;
		jlong	j_param2	= lParam;


		jmethodID method = env->GetStaticMethodID( AEWin32AccessInterface_class, "callback", "(IIJ)J");

		if ( method != NULL ){

			result = env->CallStaticLongMethod( AEWin32AccessInterface_class, method, j_msg, j_param1, j_param2 );

		}else{

			fprintf( stderr, "failed to resolve callback method\n" );
		}
	}


	jvm->DetachCurrentThread();

	return((HRESULT)result );
}


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_initialise(
	JNIEnv *env,
	jclass	cla )
{
	HANDLE hThread;

	env->GetJavaVM(&jvm);

	jclass local_ref = env->FindClass("com/biglybt/platform/win32/access/impl/AEWin32AccessInterface" );

	if ( local_ref == NULL ){

		fprintf( stderr, "failed to find AEWin32AccessInterface class\n" );

	}else{

		AEWin32AccessInterface_class = (jclass)env->NewGlobalRef( local_ref );

		env->DeleteLocalRef( local_ref );
	}

	local_ref = env->FindClass( "com/biglybt/platform/win32/access/impl/AEWin32AccessExceptionImpl" );

	if ( local_ref == NULL ){

		fprintf( stderr, "failed to find AEWin32AccessExceptionImpl class\n" );

	}else{

		AEWin32AccessExceptionImpl_class = (jclass)env->NewGlobalRef( local_ref );

		env->DeleteLocalRef( local_ref );
	}

	if ( non_unicode ){

		hThread = (HANDLE)_beginthreadex(NULL, 0, &CreateWndThreadA, NULL, 0, &uThreadId);

	}else{

		hThread = (HANDLE)_beginthreadex(NULL, 0, &CreateWndThreadW, NULL, 0, &uThreadId);
	}

	if(!hThread){

		throwException( env, "_beginthreadex", "initialisation failed" );
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_destroy(
	JNIEnv *env,
	jclass	cla )
{
	if ( uThreadId ){

		PostThreadMessage(uThreadId, WM_QUIT, 0, 0);
	}
}

// UNICODE METHODS

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getModuleNameW(
	JNIEnv		*env,
	jclass		cla )
{
	WCHAR	buffer[2048];

	if ( !GetModuleFileNameW(application_module, buffer, sizeof( buffer ))){


		throwException( env, "getModuleName", "GetModuleFileName fails" );

		return( NULL );
	}

	return( env->NewString((const jchar *)buffer, wcslen(buffer)));
}



JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getVersionW(
	JNIEnv		*env,
	jclass		cla )
{
	jstring	result = env->NewStringUTF((char *)VERSION);

	return( result );
}


JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readStringValueW(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	HKEY		key;
	HKEY		subkey;
	WCHAR		subkey_name[1024];
	WCHAR		value_name[1024];

	jstring		result	= NULL;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return( NULL );
	}

	if ( !jstringToCharsW( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return( NULL );
	}

	if ( !jstringToCharsW( env, _value_name, value_name, sizeof( value_name ))){

		return( NULL );
	}

	if ( RegOpenKeyW( key, subkey_name, &subkey ) == ERROR_SUCCESS ){

		BYTE	value[1024];
		DWORD	value_length	= sizeof( value );
		DWORD	type;

		if ( RegQueryValueExW( subkey, value_name, NULL, &type, (unsigned char*)value, &value_length ) == ERROR_SUCCESS){

			if ( type == REG_SZ || type == REG_EXPAND_SZ || type == REG_MULTI_SZ || type == REG_BINARY ){

				if ( type == REG_EXPAND_SZ ){

					WCHAR	expanded_value[2048];

					ExpandEnvironmentStringsW((const WCHAR*)value, expanded_value, sizeof( expanded_value ));

					result = env->NewString((const jchar *)expanded_value,wcslen(expanded_value));

				}else if (type == REG_BINARY) {

					// Assume binary blob is actually a 2 byte string
					result = env->NewString((const jchar *)value, value_length / 2);

				}else{


					result = env->NewString((const jchar *)value,wcslen((WCHAR *)value));
				}

			}else{

				throwException( env, "readValue", "type mismach" );
			}
		}else{

			throwException( env, "readStringValue", "RegQueryValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "readStringValue", "RegOpenKey failed" );
	}

	return( result );
}

JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readWordValueW(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	HKEY		key;
	HKEY		subkey;
	WCHAR		subkey_name[1024];
	WCHAR		value_name[1024];

	jint		result	= 0;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return( NULL );
	}

	if ( !jstringToCharsW( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return( NULL );
	}

	if ( !jstringToCharsW( env, _value_name, value_name, sizeof( value_name ))){

		return( NULL );
	}

	if ( RegOpenKeyW( key, subkey_name, &subkey ) == ERROR_SUCCESS ){

		BYTE	value[1024];
		DWORD	value_length	= sizeof( value );
		DWORD	type;

		if ( RegQueryValueExW( subkey, value_name, NULL, &type, (unsigned char*)value, &value_length ) == ERROR_SUCCESS){

			if ( type == REG_DWORD ){

				result = (LONG)value[0];

			}else{

				throwException( env, "readValue", "type mismach" );
			}
		}else{

			throwException( env, "readStringValue", "RegQueryValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "readStringValue", "RegOpenKey failed" );
	}

	return(result);
}


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeWordValueW(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name,
	jint		_value_value )
{
	HKEY		key;
	HKEY		subkey;
	WCHAR		subkey_name[1024];
	WCHAR		value_name[1024];
	DWORD		value_value	= _value_value;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsW( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( !jstringToCharsW( env, _value_name, value_name, sizeof( value_name ))){

		return;
	}



	if ( RegCreateKeyExW( key, subkey_name, 0, REG_NONE, 0, KEY_ALL_ACCESS, NULL, &subkey, NULL ) == ERROR_SUCCESS ){


		if ( RegSetValueExW( subkey, value_name, 0, REG_DWORD, (const BYTE*)&value_value, sizeof(DWORD)) == ERROR_SUCCESS){

		}else{

			throwException( env, "writeWordValue", "RegSetValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "writeWordValue", "RegCreateKeyEx failed" );
	}

}


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeStringValueW(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name,
	jstring		_value_value )
{
	HKEY		key;
	HKEY		subkey;
	WCHAR		subkey_name[1024];
	WCHAR		value_name[1024];
	WCHAR		value_value[1024];

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsW( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( !jstringToCharsW( env, _value_name, value_name, sizeof( value_name ))){

		return;
	}


	if ( !jstringToCharsW( env, _value_value, value_value, sizeof( value_value ))){

		return;
	}


	if ( RegCreateKeyExW( key, subkey_name, 0, REG_NONE, 0, KEY_ALL_ACCESS, NULL, &subkey, NULL ) == ERROR_SUCCESS ){


		if ( RegSetValueExW( subkey, value_name, 0, REG_SZ, (const BYTE*)value_value, (wcslen(value_value)+1)*sizeof(WCHAR)) == ERROR_SUCCESS){

		}else{

			throwException( env, "writeStringValue", "RegSetValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "writeStringValue", "RegCreateKeyEx failed" );
	}

}


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteKeyW(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jboolean	_recursive )
{
	HKEY		key;
	HKEY		subkey;
	WCHAR		subkey_name[1024];

	jstring		result	= NULL;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsW( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( RegOpenKeyW( key, subkey_name, &subkey ) == ERROR_SUCCESS ){


		RegCloseKey(subkey);

		if ( _recursive ){

			if ( SHDeleteKeyW( key, subkey_name ) != ERROR_SUCCESS ){

				throwException( env, "deleteKey", "SHDeleteKey failed" );
			}
		}else{

			if ( RegDeleteKeyW( key, subkey_name ) != ERROR_SUCCESS ){

				throwException( env, "deleteKey", "RegDeleteKey failed" );
			}
		}
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteValueW(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	HKEY		key;
	HKEY		subkey;
	WCHAR		subkey_name[1024];
	WCHAR		value_name[1024];

	jstring		result	= NULL;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsW( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( !jstringToCharsW( env, _value_name, value_name, sizeof( value_name ))){

		return;
	}

	if ( RegOpenKeyW( key, subkey_name, &subkey ) == ERROR_SUCCESS ){


		RegCloseKey(subkey);

		if ( SHDeleteValueW( key, subkey_name, value_name ) != ERROR_SUCCESS ){

			throwException( env, "deleteValue", "SHDeleteValue failed" );
		}
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_createProcessW(
	JNIEnv*		env,
	jclass		cla,
	jstring		_command_line,
	jboolean	_inherit_handles )
{
	WCHAR		command_line[16000];

	STARTUPINFOW			start_info;
	PROCESS_INFORMATION		proc_info;

	if ( !jstringToCharsW( env, _command_line, command_line, sizeof( command_line ))){

		return;
	}

	memset( &start_info, 0, sizeof( STARTUPINFOW ));

	start_info.cb = sizeof( STARTUPINFOW );

	if ( CreateProcessW(
			NULL,				// LPCTSTR lpApplicationName,
			command_line,		// LPTSTR lpCommandLine,
			NULL,				// LPSECURITY_ATTRIBUTES lpProcessAttributes,
			NULL,				// LPSECURITY_ATTRIBUTES lpThreadAttributes,
			_inherit_handles,	// BOOL bInheritHandles,
			DETACHED_PROCESS,	// DWORD dwCreationFlags,
			NULL,				// LPVOID lpEnvironment,
			NULL,				// LPCTSTR lpCurrentDirectory,
			&start_info,		// LPSTARTUPINFO lpStartupInfo,
			&proc_info )){		// LPPROCESS_INFORMATION lpProcessInformation


		CloseHandle( proc_info.hThread );
        CloseHandle( proc_info.hProcess );

	}else{

		throwException( env, "createProcess", "CreateProcess failed" );
	}
};


JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecAndWaitW(
	JNIEnv*		env,
	jclass		cla,
	jstring		_file,
	jstring		_params )
{
	WCHAR		file[2048];
	WCHAR		params[2048];

	if ( !jstringToCharsW( env, _file, file, sizeof( file ))){

		return(0);
	}

	if ( !jstringToCharsW( env, _params, params, sizeof( params ))){

		return(0);
	}

    SHELLEXECUTEINFOW shExecInfo;

    shExecInfo.cbSize = sizeof(SHELLEXECUTEINFOW);

    shExecInfo.fMask		= SEE_MASK_NOCLOSEPROCESS | SEE_MASK_FLAG_DDEWAIT;
    shExecInfo.hwnd			= NULL;
    shExecInfo.lpVerb		= L"runas";
    shExecInfo.lpFile		= file;
    shExecInfo.lpParameters = params;
    shExecInfo.lpDirectory	= NULL;
    shExecInfo.nShow		= SW_SHOWNORMAL;
    shExecInfo.hInstApp		= NULL;

    ShellExecuteExW(&shExecInfo);


	int res = (int)shExecInfo.hInstApp;

	if ( res <= 32 ){

		throwException( env, "shellExec", "ShellExecW failed", res );

		return( 0 );

	}else{

		HANDLE process = shExecInfo.hProcess;

		WaitForSingleObject( process, INFINITE );

		DWORD result;

		if ( GetExitCodeProcess( process, &result ) == 0 ){

			throwException( env, "shellExec", "GetExitCodeProcess failed", GetLastError());
		}

		CloseHandle( process );

		return((jint)result);
	}
};


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_moveToRecycleBinW(
	JNIEnv *env,
	jclass	cla,
	jstring _fileName )

{
	WCHAR		file_name[16000];

    SHFILEOPSTRUCTW opFile;

	if ( !jstringToCharsW( env, _fileName, file_name, sizeof( file_name )-1)){

		return;
	}

    HANDLE file = CreateFileW (	file_name,
								GENERIC_READ,
								FILE_SHARE_READ,
								NULL,
								OPEN_EXISTING,
								FILE_ATTRIBUTE_NORMAL,
								NULL );

		// Checks if file exists

    if ( file == INVALID_HANDLE_VALUE ){

		throwException( env, "moveToRecycleBin", "file not found" );

        return;
    }

    CloseHandle(file);

    file_name[ wcslen(file_name)+1 ] = 0;

    ZeroMemory(&opFile, sizeof(opFile));

    opFile.wFunc = FO_DELETE;

    opFile.pFrom = file_name;

    opFile.fFlags = FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT;

    if (SHFileOperationW (&opFile)){

        throwException( env, "moveToRecycleBin", "SHFileOperation failed" );
    }
}

#define myheapalloc(x) (HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, x))
#define myheapfree(x)  (HeapFree(GetProcessHeap(), 0, x))

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_copyPermissionW(
	JNIEnv *env,
	jclass	cla,
	jstring _fileNameIn,
	jstring _fileNameOut )
{
	WCHAR		file_name_in[2048];
	WCHAR		file_name_out[2048];

	if ( !jstringToCharsW( env, _fileNameIn, file_name_in, sizeof( file_name_in )-1)){

		return;
	}

	if ( !jstringToCharsW( env, _fileNameOut, file_name_out, sizeof( file_name_out )-1)){

		return;
	}

	SECURITY_INFORMATION secInfo	= DACL_SECURITY_INFORMATION;
	DWORD				 cbFileSD   = 0;
	PSECURITY_DESCRIPTOR pFileSD	= NULL;

    BOOL ok = GetFileSecurityW(file_name_in, secInfo, pFileSD, 0, &cbFileSD);

      // API should have failed with insufficient buffer.

	if ( ok ){

		throwException( env, "copyPermission", "GetFileSecurity ok" );

        return;

    }else if (GetLastError() == ERROR_FILE_NOT_FOUND) {

		throwException( env, "copyPermission", "from file not found" );

		return;

	}else if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {

		throwException( env, "copyPermission", "GetFileSecurity unexpected response", GetLastError() );

        return;

	}else{

		pFileSD	= myheapalloc( cbFileSD );

		if (!pFileSD) {

			throwException( env, "copyPermission", "no memory" );

			return;
		}

		ok = GetFileSecurityW(file_name_in, secInfo, pFileSD, cbFileSD, &cbFileSD );

		if (!ok) {

			myheapfree( pFileSD );

			throwException( env, "copyPermission", "GetFileSecurity", GetLastError());

			return;
		}

		ok = SetFileSecurityW( file_name_out, secInfo, pFileSD );

	 	myheapfree( pFileSD );

		if ( !ok ){

			if (GetLastError() == ERROR_FILE_NOT_FOUND) {

				throwException( env, "copyPermission", "to file not found" );

			}else{

				throwException( env, "copyPermission", "SetFileSecurity unexpected response", GetLastError() );

			}
		}
	}
}

JNIEXPORT jboolean JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_testNativeAvailabilityW(
	JNIEnv *env,
	jclass	cla,
	jstring _name )
{
	WCHAR		name[2048];

	if ( !jstringToCharsW( env, _name, name, sizeof( name )-1)){

		return( 0 );
	}


	HMODULE	mod =
		LoadLibraryExW(
			name,
			NULL,
			LOAD_LIBRARY_AS_DATAFILE );

	if ( mod == NULL ){

		return( 0 );

	}else{

		FreeLibrary( mod );

		return( 1 );
	}
}

// 1.3
JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecuteW(
	JNIEnv	*env,
	jclass	cla,
	jstring _operation,
	jstring	_file,
	jstring _parameters,
	jstring _directory,
	jint _showCmd )
{
	WCHAR		operation[20];
	WCHAR		file[5192];
	WCHAR		parameters[16000];
	WCHAR		directory[5192];
	INT			showCmd = _showCmd;

	if ( !jstringToCharsW( env, _operation, operation, sizeof( operation ))){
		return -1;
	}

	if ( !jstringToCharsW( env, _file, file, sizeof( file ))){
		return -1;
	}

	if ( !jstringToCharsW( env, _parameters, parameters, sizeof( parameters ))){
		return -1;
	}

	if ( !jstringToCharsW( env, _directory, directory, sizeof( directory ))){
		return -1;
	}

	// Not sure if ShellExecute treats "\0" as NULL, so do explicit check
	return(jint) ShellExecuteW(NULL,
			_operation == NULL ? NULL : operation,
			_file == NULL ? NULL : file,
			_parameters == NULL ? NULL : parameters,
			_directory == NULL ? NULL : directory,
			showCmd);
}


// NON-UNICODE VARIANT FOR WIN95,98,ME




bool
jstringToCharsA(
	JNIEnv		*env,
	jstring		jstr,
	char		*chars,
	int			chars_len )
{
	if ( jstr == NULL ){

		chars[0] = 0;

	}else{

		int	jdata_len = env->GetStringLength(jstr);

		if ( jdata_len >= chars_len ){

			throwException( env, "jstringToChars", "jstring truncation occurred" );

			return( false );
		}

		const jchar *jdata = env->GetStringChars( jstr, NULL );

		for (int i=0;i<jdata_len;i++){

			chars[i] = (char)jdata[i];
		}

		chars[jdata_len]=0;

		env->ReleaseStringChars( jstr, jdata );
	}

	return( true );
}

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getModuleNameA(
	JNIEnv		*env,
	jclass		cla )
{
	char	buffer[2048];

	if ( !GetModuleFileNameA(application_module, buffer, sizeof( buffer ))){


		throwException( env, "getModuleName", "GetModuleFileName fails" );

		return( NULL );
	}

	return( env->NewStringUTF((char *)buffer));
}



JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getVersionA(
	JNIEnv		*env,
	jclass		cla )
{
	jstring	result = env->NewStringUTF((char *)VERSION);

	return( result );
}


JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readStringValueA(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	HKEY		key;
	HKEY		subkey;
	char		subkey_name[1024];
	char		value_name[1024];

	jstring		result	= NULL;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return( NULL );
	}

	if ( !jstringToCharsA( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return( NULL );
	}

	if ( !jstringToCharsA( env, _value_name, value_name, sizeof( value_name ))){

		return( NULL );
	}

	if ( RegOpenKeyA( key, subkey_name, &subkey ) == ERROR_SUCCESS ){

		BYTE	value[1024];
		DWORD	value_length	= sizeof( value );
		DWORD	type;

		if ( RegQueryValueExA( subkey, value_name, NULL, &type, (unsigned char*)value, &value_length ) == ERROR_SUCCESS){

			if ( type == REG_SZ || type == REG_EXPAND_SZ || type == REG_MULTI_SZ ){

				if ( type == REG_EXPAND_SZ ){

					char	expanded_value[2048];

					ExpandEnvironmentStringsA((const char*)value, expanded_value, sizeof( expanded_value ));

					result = env->NewStringUTF((char *)expanded_value);

				}else{


					result = env->NewStringUTF((char *)value);
				}

			}else{

				throwException( env, "readValue", "type mismach" );
			}
		}else{

			throwException( env, "readStringValue", "RegQueryValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "readStringValue", "RegOpenKey failed" );
	}

	return( result );
}

JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readWordValueA(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	HKEY		key;
	HKEY		subkey;
	char		subkey_name[1024];
	char		value_name[1024];

	jint		result	= 0;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return( NULL );
	}

	if ( !jstringToCharsA( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return( NULL );
	}

	if ( !jstringToCharsA( env, _value_name, value_name, sizeof( value_name ))){

		return( NULL );
	}

	if ( RegOpenKeyA( key, subkey_name, &subkey ) == ERROR_SUCCESS ){

		BYTE	value[1024];
		DWORD	value_length	= sizeof( value );
		DWORD	type;

		if ( RegQueryValueExA( subkey, value_name, NULL, &type, (unsigned char*)value, &value_length ) == ERROR_SUCCESS){

			if ( type == REG_DWORD ){

				result = (LONG)value[0];

			}else{

				throwException( env, "readValue", "type mismach" );
			}
		}else{

			throwException( env, "readStringValue", "RegQueryValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "readStringValue", "RegOpenKey failed" );
	}

	return(result);
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeWordValueA(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name,
	jint		_value_value )
{
	HKEY		key;
	HKEY		subkey;
	char		subkey_name[1024];
	char		value_name[1024];
	DWORD		value_value	= _value_value;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsA( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( !jstringToCharsA( env, _value_name, value_name, sizeof( value_name ))){

		return;
	}



	if ( RegCreateKeyExA( key, subkey_name, 0, REG_NONE, 0, KEY_ALL_ACCESS, NULL, &subkey, NULL ) == ERROR_SUCCESS ){


		if ( RegSetValueExA( subkey, value_name, 0, REG_DWORD, (const BYTE*)&value_value, sizeof(DWORD)) == ERROR_SUCCESS){

		}else{

			throwException( env, "writeWordValue", "RegSetValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "writeWordValue", "RegCreateKeyEx failed" );
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeStringValueA(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name,
	jstring		_value_value )
{
	HKEY		key;
	HKEY		subkey;
	char		subkey_name[1024];
	char		value_name[1024];
	char		value_value[1024];

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsA( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( !jstringToCharsA( env, _value_name, value_name, sizeof( value_name ))){

		return;
	}


	if ( !jstringToCharsA( env, _value_value, value_value, sizeof( value_value ))){

		return;
	}


	if ( RegCreateKeyExA( key, subkey_name, 0, REG_NONE, 0, KEY_ALL_ACCESS, NULL, &subkey, NULL ) == ERROR_SUCCESS ){


		if ( RegSetValueExA( subkey, value_name, 0, REG_SZ, (const BYTE*)value_value, strlen(value_value)+1 ) == ERROR_SUCCESS){

		}else{

			throwException( env, "writeStringValue", "RegSetValueEx failed" );
		}

		RegCloseKey(subkey);

	}else{

		throwException( env, "writeStringValue", "RegCreateKeyEx failed" );
	}

}


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteKeyA(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jboolean	_recursive )
{
	HKEY		key;
	HKEY		subkey;
	char		subkey_name[1024];

	jstring		result	= NULL;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsA( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( RegOpenKeyA( key, subkey_name, &subkey ) == ERROR_SUCCESS ){


		RegCloseKey(subkey);

		if ( _recursive ){

			if ( SHDeleteKeyA( key, subkey_name ) != ERROR_SUCCESS ){

				throwException( env, "deleteKey", "SHDeleteKey failed" );
			}
		}else{

			if ( RegDeleteKeyA( key, subkey_name ) != ERROR_SUCCESS ){

				throwException( env, "deleteKey", "RegDeleteKey failed" );
			}
		}
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteValueA(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	HKEY		key;
	HKEY		subkey;
	char		subkey_name[1024];
	char		value_name[1024];

	jstring		result	= NULL;

	key	= mapHKEY( env, _type );

	if ( key == NULL ){

		return;
	}

	if ( !jstringToCharsA( env, _subkey_name, subkey_name, sizeof( subkey_name ))){

		return;
	}

	if ( !jstringToCharsA( env, _value_name, value_name, sizeof( value_name ))){

		return;
	}

	if ( RegOpenKeyA( key, subkey_name, &subkey ) == ERROR_SUCCESS ){


		RegCloseKey(subkey);

		if ( SHDeleteValueA( key, subkey_name, value_name ) != ERROR_SUCCESS ){

			throwException( env, "deleteValue", "SHDeleteValue failed" );
		}
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_createProcessA(
	JNIEnv*		env,
	jclass		cla,
	jstring		_command_line,
	jboolean	_inherit_handles )
{
	char		command_line[16000];

	STARTUPINFOA			start_info;
	PROCESS_INFORMATION		proc_info;

	if ( !jstringToCharsA( env, _command_line, command_line, sizeof( command_line ))){

		return;
	}

	memset( &start_info, 0, sizeof( STARTUPINFOA ));

	start_info.cb = sizeof( STARTUPINFOA );

	if ( CreateProcessA(
			NULL,				// LPCTSTR lpApplicationName,
			command_line,		// LPTSTR lpCommandLine,
			NULL,				// LPSECURITY_ATTRIBUTES lpProcessAttributes,
			NULL,				// LPSECURITY_ATTRIBUTES lpThreadAttributes,
			_inherit_handles,	// BOOL bInheritHandles,
			DETACHED_PROCESS,	// DWORD dwCreationFlags,
			NULL,				// LPVOID lpEnvironment,
			NULL,				// LPCTSTR lpCurrentDirectory,
			&start_info,		// LPSTARTUPINFO lpStartupInfo,
			&proc_info )){		// LPPROCESS_INFORMATION lpProcessInformation


		CloseHandle( proc_info.hThread );
        CloseHandle( proc_info.hProcess );

	}else{

		throwException( env, "createProcess", "CreateProcess failed" );
	}
};

JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecAndWaitA(
	JNIEnv*		env,
	jclass		cla,
	jstring		_file,
	jstring		_params )
{
	char		file[2048];
	char		params[2048];

	if ( !jstringToCharsA( env, _file, file, sizeof( file ))){

		return(0);
	}

	if ( !jstringToCharsA( env, _params, params, sizeof( params ))){

		return(0);
	}

    SHELLEXECUTEINFOA shExecInfo;

    shExecInfo.cbSize = sizeof(SHELLEXECUTEINFOA);

    shExecInfo.fMask		= SEE_MASK_NOCLOSEPROCESS | SEE_MASK_FLAG_DDEWAIT;
    shExecInfo.hwnd			= NULL;
    shExecInfo.lpVerb		= "runas";
    shExecInfo.lpFile		= file;
    shExecInfo.lpParameters = params;
    shExecInfo.lpDirectory	= NULL;
    shExecInfo.nShow		= SW_SHOWNORMAL;
    shExecInfo.hInstApp		= NULL;

    ShellExecuteExA(&shExecInfo);


	int res = (int)shExecInfo.hInstApp;

	if ( res <= 32 ){

		throwException( env, "shellExec", "ShellExecA failed", res );

		return( 0 );

	}else{

		HANDLE process = shExecInfo.hProcess;

		WaitForSingleObject( process, INFINITE );

		DWORD result;

		if ( GetExitCodeProcess( process, &result ) == 0 ){

			throwException( env, "shellExec", "GetExitCodeProcess failed", GetLastError());
		}

		CloseHandle( process );

		return((jint)result);
	}
};

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_moveToRecycleBinA(
	JNIEnv *env,
	jclass	cla,
	jstring _fileName )

{
	char		file_name[16000];

    SHFILEOPSTRUCTA opFile;

	if ( !jstringToCharsA( env, _fileName, file_name, sizeof( file_name )-1)){

		return;
	}

    HANDLE file = CreateFileA (	file_name,
								GENERIC_READ,
								FILE_SHARE_READ,
								NULL,
								OPEN_EXISTING,
								FILE_ATTRIBUTE_NORMAL,
								NULL );

		// Checks if file exists

    if ( file == INVALID_HANDLE_VALUE ){

		throwException( env, "moveToRecycleBin", "file not found" );

        return;
    }

    CloseHandle(file);

    file_name[ strlen(file_name)+1 ] = 0;

    ZeroMemory(&opFile, sizeof(opFile));

    opFile.wFunc = FO_DELETE;

    opFile.pFrom = file_name;

    opFile.fFlags = FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT;

    if (SHFileOperationA (&opFile)){

        throwException( env, "moveToRecycleBin", "SHFileOperation failed" );
    }
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_copyPermissionA(
	JNIEnv *env,
	jclass	cla,
	jstring _fileNameIn,
	jstring _fileNameOut )
{
	char		file_name_in[2048];
	char		file_name_out[2048];

	if ( !jstringToCharsA( env, _fileNameIn, file_name_in, sizeof( file_name_in )-1)){

		return;
	}

	if ( !jstringToCharsA( env, _fileNameOut, file_name_out, sizeof( file_name_out )-1)){

		return;
	}

	SECURITY_INFORMATION secInfo	= DACL_SECURITY_INFORMATION;
	DWORD				 cbFileSD   = 0;
	PSECURITY_DESCRIPTOR pFileSD	= NULL;

    BOOL ok = GetFileSecurityA(file_name_in, secInfo, pFileSD, 0, &cbFileSD);

      // API should have failed with insufficient buffer.

	if ( ok ){

		throwException( env, "copyPermission", "GetFileSecurity ok" );

        return;

    }else if (GetLastError() == ERROR_FILE_NOT_FOUND) {

		throwException( env, "copyPermission", "from file not found" );

		return;

	}else if (GetLastError() != ERROR_INSUFFICIENT_BUFFER) {

		throwException( env, "copyPermission", "GetFileSecurity unexpected response", GetLastError() );

        return;

	}else{

		pFileSD	= myheapalloc( cbFileSD );

		if (!pFileSD) {

			throwException( env, "copyPermission", "no memory" );

			return;
		}

		ok = GetFileSecurityA(file_name_in, secInfo, pFileSD, cbFileSD, &cbFileSD );

		if (!ok) {

			myheapfree( pFileSD );

			throwException( env, "copyPermission", "GetFileSecurity", GetLastError());

			return;
		}

		ok = SetFileSecurityA( file_name_out, secInfo, pFileSD );

	 	myheapfree( pFileSD );

		if ( !ok ){

			if (GetLastError() == ERROR_FILE_NOT_FOUND) {

				throwException( env, "copyPermission", "to file not found" );

			}else{

				throwException( env, "copyPermission", "SetFileSecurity unexpected response", GetLastError() );

			}
		}
	}
}

JNIEXPORT jboolean JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_testNativeAvailabilityA(
	JNIEnv *env,
	jclass	cla,
	jstring _name )
{
	char		name[2048];

	if ( !jstringToCharsA( env, _name, name, sizeof( name )-1)){

		return( 0 );
	}


	HMODULE	mod =
		LoadLibraryExA(
			name,
			NULL,
			LOAD_LIBRARY_AS_DATAFILE );

	if ( mod == NULL ){

		return( 0 );

	}else{

		FreeLibrary( mod );

		return( 1 );
	}
}

// 1.3
JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecuteA(
	JNIEnv	*env,
	jclass	cla,
	jstring _operation,
	jstring	_file,
	jstring _parameters,
	jstring _directory,
	jint _showCmd )
{
	char	operation[20];
	char	file[5192];
	char	parameters[16000];
	char	directory[5192];
	INT		showCmd = _showCmd;

	if ( !jstringToCharsA( env, _operation, operation, sizeof( operation ))){
		return -1;
	}

	if ( !jstringToCharsA( env, _file, file, sizeof( file ))){
		return -1;
	}

	if ( !jstringToCharsA( env, _parameters, parameters, sizeof( parameters ))){
		return -1;
	}

	if ( !jstringToCharsA( env, _directory, directory, sizeof( directory ))){
		return -1;
	}

	// Not sure if ShellExecute treats "\0" as NULL, so do explicit check
	return (jint)ShellExecuteA(NULL,
			_operation == NULL ? NULL : operation,
			_file == NULL ? NULL : file,
			_parameters == NULL ? NULL : parameters,
			_directory == NULL ? NULL : directory,
			showCmd);
}


// BLAH

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getModuleName(
	JNIEnv		*env,
	jclass		cla )
{
	if ( non_unicode ){
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getModuleNameA( env, cla ));
	}else{
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getModuleNameW( env, cla ));
	}
}

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getVersion(
	JNIEnv		*env,
	jclass		cla )
{
	if ( non_unicode ){
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getVersionA( env, cla ));
	}else{
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_getVersionW( env, cla ));
	}
}

JNIEXPORT jstring JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readStringValue(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	if ( non_unicode ){
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readStringValueA( env, cla, _type, _subkey_name, _value_name ));
	}else{
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readStringValueW( env, cla, _type, _subkey_name, _value_name ));
	}
}

JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readWordValue(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	if ( non_unicode ){
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readWordValueA( env, cla, _type, _subkey_name, _value_name ));
	}else{
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_readWordValueW( env, cla, _type, _subkey_name, _value_name ));
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeStringValue(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name,
	jstring		_value_value )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeStringValueA( env, cla, _type, _subkey_name, _value_name, _value_value );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeStringValueW( env, cla, _type, _subkey_name, _value_name, _value_value );
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeWordValue(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name,
	jint		_value_value )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeWordValueA( env, cla, _type, _subkey_name, _value_name, _value_value );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_writeWordValueW( env, cla, _type, _subkey_name, _value_name, _value_value );
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteKey(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jboolean	_recursive )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteKeyA( env, cla, _type, _subkey_name, _recursive );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteKeyW( env, cla, _type, _subkey_name, _recursive );
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteValue(
	JNIEnv		*env,
	jclass		cla,
	jint		_type,
	jstring		_subkey_name,
	jstring		_value_name )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteValueA( env, cla, _type, _subkey_name, _value_name );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_deleteValueW( env, cla, _type, _subkey_name, _value_name );
	}
}

JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_createProcess(
	JNIEnv*		env,
	jclass		cla,
	jstring		_command_line,
	jboolean	_inherit_handles )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_createProcessA( env, cla, _command_line, _inherit_handles );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_createProcessW( env, cla, _command_line, _inherit_handles );
	}
}


JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_moveToRecycleBin(
	JNIEnv*		env,
	jclass		cla,
	jstring		_file_name )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_moveToRecycleBinA( env, cla, _file_name );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_moveToRecycleBinW( env, cla, _file_name );
	}
}



JNIEXPORT void JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_copyPermission(
	JNIEnv *env,
	jclass	cla,
	jstring _fileNameIn,
	jstring _fileNameOut )
{
	if ( non_unicode ){
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_copyPermissionA( env, cla, _fileNameIn, _fileNameOut );
	}else{
		Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_copyPermissionW( env, cla, _fileNameIn, _fileNameOut );
	}
}

JNIEXPORT jboolean JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_testNativeAvailability(
	JNIEnv	*env,
	jclass	cla,
	jstring	name )
{
	if ( non_unicode ){
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_testNativeAvailabilityA( env, cla,name ));
	}else{
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_testNativeAvailabilityW( env, cla, name ));
	}
}

JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecute(
	JNIEnv	*env,
	jclass	cla,
	jstring operation,
	jstring	file,
	jstring parameters,
	jstring directory,
	jint showCmd )
{
	if ( non_unicode ){
		return
			Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecuteA(
				env, cla, operation, file, parameters, directory, showCmd );
	}else{
		return
			Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecuteW(
				env, cla, operation, file, parameters, directory, showCmd );
	}
}

JNIEXPORT jint JNICALL
Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecuteAndWait(
	JNIEnv*		env,
	jclass		cla,
	jstring		_command_line,
	jstring		_params )
{
	if ( non_unicode ){
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecAndWaitA( env, cla, _command_line, _params ));
	}else{
		return( Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_shellExecAndWaitW( env, cla, _command_line, _params ));
	}
}

JNIEXPORT jint JNICALL Java_com_biglybt_platform_win32_access_impl_AEWin32AccessInterface_setThreadExecutionState
  (JNIEnv *env, jclass cla, jint esStates)
{
	return SetThreadExecutionState((EXECUTION_STATE) esStates);
}


