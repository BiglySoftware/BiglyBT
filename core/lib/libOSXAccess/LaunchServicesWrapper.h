//
//  LaunchServicesWrapper.h
//  SetDefaultApplication
//
//  Created by Stefan BALU on 6/21/13.
//  Copyright (c) Azureus Software, Inc. All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details ( see the LICENSE file ).
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

#import <Foundation/Foundation.h>

@interface LaunchServicesWrapper : NSObject

+ (NSString *)UTIforFileMimeType:(NSString *)mimetype;
+ (NSString *)UTIforFileExtension:(NSString *)extension;

+ (NSString *)defaultApplicationForExtension:(NSString *)extension;
+ (NSString *)defaultApplicationForMimeType:(NSString *)mimetype;
+ (NSString *)defaultApplicationForScheme:(NSString *)scheme;

+ (BOOL)setDefaultApplication:(NSString *)bundleID forExtension:(NSString *)extension;
+ (BOOL)setDefaultApplication:(NSString *)bundleID forMimeType:(NSString *)mimetype;
+ (BOOL)setDefaultApplication:(NSString *)bundleID forScheme:(NSString *)scheme;

@end

