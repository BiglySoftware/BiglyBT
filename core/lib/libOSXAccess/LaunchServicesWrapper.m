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

#import <ApplicationServices/ApplicationServices.h>
#import "LaunchServicesWrapper.h"

@implementation LaunchServicesWrapper

+ (NSString *)UTIforFileMimeType:(NSString *)mimetype
{
    return (NSString *)CFBridgingRelease(
        UTTypeCreatePreferredIdentifierForTag(kUTTagClassMIMEType, (CFStringRef)mimetype, NULL)
    );
}

+ (NSString *)UTIforFileExtension:(NSString *)extension
{
    return (NSString *)CFBridgingRelease(
        UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, (CFStringRef)extension, NULL)
    );
}

+ (NSString *)defaultApplicationForExtension:(NSString *)extension
{
    return (NSString *)CFBridgingRelease(
        LSCopyDefaultRoleHandlerForContentType((CFStringRef)[LaunchServicesWrapper UTIforFileExtension:extension], kLSRolesAll)
    );
}

+ (NSString *)defaultApplicationForMimeType:(NSString *)mimetype
{
    return (NSString *)CFBridgingRelease(
        LSCopyDefaultRoleHandlerForContentType((CFStringRef)[LaunchServicesWrapper UTIforFileMimeType:mimetype], kLSRolesAll)
    );
}

+ (NSString *)defaultApplicationForScheme:(NSString *)scheme
{
    return (NSString *)CFBridgingRelease(
        LSCopyDefaultHandlerForURLScheme((CFStringRef)scheme)
    );
}

+ (BOOL)setDefaultApplication:(NSString *)bundleID forExtension:(NSString *)extension
{
    return LSSetDefaultRoleHandlerForContentType(
                                                 (CFStringRef)[LaunchServicesWrapper UTIforFileExtension:extension], kLSRolesAll, (CFStringRef)bundleID);
}

+ (BOOL)setDefaultApplication:(NSString *)bundleID forMimeType:(NSString *)mimetype
{
    return LSSetDefaultRoleHandlerForContentType((CFStringRef)[LaunchServicesWrapper UTIforFileMimeType:mimetype], kLSRolesAll, (CFStringRef)bundleID);
}

+ (BOOL)setDefaultApplication:(NSString *)bundleID forScheme:(NSString *)scheme
{
    return LSSetDefaultHandlerForURLScheme((CFStringRef)scheme, (CFStringRef)bundleID);
}

@end