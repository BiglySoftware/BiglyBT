/*
 * File    : UISwingImageRepository.java
 * Created : 31-Mar-2004
 * By      : parg
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

package com.biglybt.ui.swing;

/**
 * This class is used by the remote applet UI.
 *
 * @author parg
 *
 */

import java.io.*;
import java.awt.*;
import javax.swing.*;
import javax.imageio.*;

import com.biglybt.ui.common.UIImageRepository;

public class
UISwingImageRepository
{
	public static Image
	getImage(
		String		name )
	{
		try{
			return(ImageIO.read(UIImageRepository.getImageAsStream(name)));

		}catch( Throwable e ){

				// some versions of Opera don't have the imageio stuff available it seems
				// so catch all errors and return null

			e.printStackTrace();

			return( null );
		}
	}

	public static InputStream
	getImageAsStream(
		String		name )
	{
		return( UIImageRepository.getImageAsStream(name));
	}

	public static Image
	getImage(
		InputStream		is )
	{
		try{
			return(ImageIO.read(is));

		}catch( Throwable e ){

			e.printStackTrace();

			return( null );
		}
	}

	public static Icon
	getIcon(
		String		name )
	{
		Image	image = getImage( name );

		return( image==null?null:new ImageIcon( image ));
	}

	public static Icon
	getIcon(
		InputStream		is )
	{
		Image	image = getImage( is );

		return( image==null?null:new ImageIcon( image ));
	}
}
