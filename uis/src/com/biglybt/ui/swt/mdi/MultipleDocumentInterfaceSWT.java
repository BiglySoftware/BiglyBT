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
 */
package com.biglybt.ui.swt.mdi;

import org.eclipse.swt.widgets.Composite;

import com.biglybt.ui.mdi.MdiEntry;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.pif.UISWTViewEventListener;
import com.biglybt.ui.swt.pifimpl.UISWTViewBuilderCore;
import com.biglybt.ui.swt.skin.SWTSkinObject;

/**
 * @author TuxPaper
 * @created Jan 29, 2010
 *
 */
public interface MultipleDocumentInterfaceSWT
	extends MultipleDocumentInterface
{
	MdiEntrySWT getEntryBySkinView(Object skinView);

	/**
	 * @implNote Still used by EMP.  Remove after 2.1.0.1
	 * @deprecated
	 */
	public MdiEntry createEntryFromEventListener(String parentEntryID,
			UISWTViewEventListener l, String id, boolean closeable,
			Object datasource, String preferredAfterID);

	/**
	 * 
	 * @return Newly created MDI Entry
	 */
	MdiEntrySWT createEntry(UISWTViewBuilderCore builder, boolean closeable);

	@Override
	MdiEntrySWT getEntry(String id);

	@Override
	MdiEntrySWT[] getEntries();

	@Override
	MdiEntrySWT getCurrentEntry();

	/**
	 * @param closeableConfigFile
	 * @since 5.6.0.1
	 */
	void setCloseableConfigFile(String closeableConfigFile);

	/**
	 * Builds MDI and populates it with entries registered to id or datasourcetype
	 * @param parent
	 */
	void buildMDI(Composite parent);

	void buildMDI(SWTSkinObject skinObject);
}
