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

#import <Cocoa/Cocoa.h>

// Uncomment if you want debug to Console
#define fprintf

NSMutableDictionary *map;
Boolean useNSWorkspace;

@interface IONotification : NSObject {
	IONotificationPortRef gNotifyPort;
}

-(void)setup;
-(void)setupLight;
-(void)mount:(id)notification;
-(void)unmount:(id)notification;
-(int)checkExisting;

- (void)rawDeviceAdded:(io_iterator_t)iterator;

//void rawDeviceAdded(void *refCon, io_iterator_t iterator);
void DeviceNotification(void *refCon, io_service_t service, natural_t messageType, void *messageArgument);

@end


@interface StatfsObject : NSObject
{
@public
	struct statfs *fs;
}
@end
