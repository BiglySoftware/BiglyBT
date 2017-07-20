/*
 * Created on Jan 1, 2009 3:17:06 PM
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
 */
package com.biglybt.ui.swt.imageloader;

import org.eclipse.swt.graphics.Image;

/**
 * @author TuxPaper
 * @created Jan 1, 2009
 *
 */
public class ImageLoaderRefInfo
{
	private Image[] images;

	// -2: non-disposable; -1: someone over unref'd; 0: no refs (dispose!)
	private long refcount;

	protected ImageLoaderRefInfo(Image[] images) {
		this.images = images;
		refcount = 1;
	}

	protected ImageLoaderRefInfo(Image image) {
		this.images = new Image[] {
			image
		};
		refcount = 1;
	}

	protected void setNonDisposable() {
		refcount = -2;
	}

	protected boolean isNonDisposable() {
		return refcount == -2;
	}

	protected void addref() {
		synchronized (this) {
  		if (refcount >= 0) {
  			refcount++;
  		}
		}
	}

	protected void unref() {
		synchronized (this) {
			if (refcount >= 0) {
				refcount--;
			}
		}
	}

	protected boolean canDispose() {
		return refcount == 0 || refcount == -1;
	}

	protected long getRefCount() {
		return refcount;
	}

	protected Image[] getImages() {
		return images;
	}

	protected void setImages(Image[] images) {
		this.images = images;
	}

	protected String
	getString()
	{
		String img_str = "";

		for ( Image i: images ){

			String s;

			if ( i == null ){

				s = "null";

			}else{

				s = i.toString() + ", disp=" + i.isDisposed();
			}

			img_str += (img_str.length()==0?"":",") + s;
		}

		return( "rc=" + refcount + ", images=[" + img_str + "]" );
	}
}
