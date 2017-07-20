/*
 * Created on Jul 14, 2009
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.ui.swt.devices.add;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.ui.swt.Utils;

import com.biglybt.core.devices.*;
import com.biglybt.core.devices.DeviceManager.DeviceManufacturer;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.SkinnedDialog.SkinnedDialogClosedListener;

/**
 * @author TuxPaper
 * @created Jul 14, 2009
 *
 */
public class ManufacturerChooser
{
	private SkinnedDialog skinnedDialog;
	private ClosedListener listener;
	protected DeviceManufacturer chosenMF;

	public void open(ClosedListener l) {
		this.listener = l;
		skinnedDialog = new SkinnedDialog("skin3_dlg_deviceadd_mfchooser",
				"shell", SWT.TITLE | SWT.BORDER);

		skinnedDialog.addCloseListener(new SkinnedDialogClosedListener() {
			@Override
			public void skinDialogClosed(SkinnedDialog dialog) {
				if (listener != null) {
					listener.MfChooserClosed(chosenMF);
				}
			}
		});

		SWTSkin skin = skinnedDialog.getSkin();
		SWTSkinObject so= skin.getSkinObject("list");
		if (so instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer soList = (SWTSkinObjectContainer) so;

			Composite parent = soList.getComposite();

			Canvas centerCanvas = new Canvas(parent, SWT.NONE);
			FormData fd = Utils.getFilledFormData();
			fd.bottom = null;
			fd.height = 0;
			centerCanvas.setLayoutData(fd);


			Composite area = new Composite(parent, SWT.NONE);
			RowLayout rowLayout = new RowLayout(SWT.VERTICAL);
			rowLayout.fill = true;
			Utils.setLayout(area, rowLayout);
			fd = Utils.getFilledFormData();
			fd.left = new FormAttachment(centerCanvas, 50, SWT.CENTER);
			fd.right = null;
			area.setLayoutData(fd);

			Listener btnListener = new Listener() {
				@Override
				public void handleEvent(Event event) {
					chosenMF = (DeviceManufacturer) event.widget.getData("mf");
					skinnedDialog.close();
				}
			};

			DeviceManager deviceManager = DeviceManagerFactory.getSingleton();
			DeviceManufacturer[] mfs = deviceManager.getDeviceManufacturers(Device.DT_MEDIA_RENDERER);
			for (DeviceManufacturer mf : mfs) {
				DeviceTemplate[] deviceTemplates = mf.getDeviceTemplates();
				boolean hasNonAuto = false;
				for (DeviceTemplate deviceTemplate : deviceTemplates) {
					if (!deviceTemplate.isAuto()) {
						hasNonAuto = true;
						break;
					}
				}
				if (!hasNonAuto) {
					continue;
				}
				Button button = new Button(area, SWT.PUSH);
				button.setText(mf.getName());
				button.setData("mf", mf);
				button.addListener(SWT.MouseUp, btnListener);
			}
		}

		skinnedDialog.getShell().pack();
		skinnedDialog.open();
	}

	public static interface ClosedListener {
		public void MfChooserClosed(DeviceManufacturer mf);
	}
}
