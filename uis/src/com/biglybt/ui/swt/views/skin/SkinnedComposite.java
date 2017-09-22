/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.views.skin;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinFactory;


public class SkinnedComposite
{
	private SWTSkin skin;

	public SkinnedComposite( Composite comp ) {
		this( "skin3_composite", "composite", comp );
	}
	
	public SkinnedComposite(String skinFile, String shellSkinObjectID, Composite comp) {
		this(SkinnedComposite.class.getClassLoader(), "com/biglybt/ui/skin/",
				skinFile, shellSkinObjectID, comp );
	}

	public 
	SkinnedComposite(
		ClassLoader cla, 
		String 		skinPath, 
		String 		skinFile,
		String 		shellSkinObjectID, 
		Composite	comp )
	{
		skin = SWTSkinFactory.getNonPersistentInstance(cla, skinPath,
				skinFile + ".properties");

		skin.initialize( comp, shellSkinObjectID);
	}

	public SWTSkin getSkin() {
		return skin;
	}
}
