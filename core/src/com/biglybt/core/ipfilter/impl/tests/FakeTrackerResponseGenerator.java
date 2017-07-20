/*
 * Created on 30 sept. 2004
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.core.ipfilter.impl.tests;

/**
 * @author Olivier Chalouhi
 *
 */
public class FakeTrackerResponseGenerator {

	//This will generate a fake announce with loads of bad IPs in it.
	public static void main(String args[]) {
		String baseRange = "195.68.236.";
		String basePeerId = "-AZ2104-0VR73lDzLejd";
		System.out.print("d8:intervali10e5:peersl");
		for(int i = 100 ; i < 200 ; i++) {
			String iStr = "" + i;
			int iStrLength  = iStr.length();
			String ip = baseRange + iStr;
			String peerId = basePeerId.substring(0,20-iStrLength) + iStr;
			System.out.print("d2:ip" + ip.length() + ":" + ip);
			System.out.print("7:peer id20:" + peerId);
			System.out.print("4:porti3003ee");
		}
		System.out.print("ee");
	}
}
