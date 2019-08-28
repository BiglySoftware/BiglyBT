/*
 * Created on 2004/May/23
 * Created by TuxPaper
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

package com.biglybt.ui.swt.pifimpl;


import org.eclipse.swt.graphics.Image;

import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTGraphic;

/** An SWT image to be used in Azureus
 *
 * @see UISWTInstanceImpl#createGraphic
 */
public class
UISWTGraphicImpl
	implements UISWTGraphic
{
	Image img;

  public UISWTGraphicImpl(Image newImage) {
    img = newImage;
  }

  @Override
  public Image getImage() {
  	if (img == null || img.isDisposed()) {
  		return null;
  	}
	  try {
		  img.getDevice();
	  } catch (Throwable t) {
  		return null;
	  }
    return img;
  }

  @Override
  public boolean setImage(Image newImage) {
    if (img == newImage)
      return false;
    img = newImage;
    return true;
  }

  @Override
  public boolean equals(Object obj) {
  	if (super.equals(obj)) {
  		return true;
  	}
  	if (obj instanceof UISWTGraphic) {
  		Image img2 = ((UISWTGraphic) obj).getImage();
  		if (img2 == null) {
  			return img == null;
  		}
  		return img2.equals(img);
  	}
  	return false;
  }

	@Override
	public void dispose() {
  	if (img != null) {
			Utils.execSWTThread(() -> Utils.disposeSWTObjects(img));
			img = null;
	  }
	}
}
