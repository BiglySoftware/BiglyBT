/*
 * Created on Jul 16, 2009
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

import com.biglybt.core.devices.DeviceManager.DeviceManufacturer;
import com.biglybt.core.devices.DeviceTemplate;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.devices.TranscodeChooser;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.skin.SWTSkin;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.ui.swt.skin.SWTSkinObjectContainer;
import com.biglybt.ui.swt.skin.SWTSkinObjectText;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;
import com.biglybt.ui.swt.views.skin.SkinnedDialog.SkinnedDialogClosedListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author TuxPaper
 * @created Jul 16, 2009
 *
 */
public class DeviceTemplateChooser
{
	DeviceTemplateClosedListener listener;
	SkinnedDialog skinnedDialog;
	DeviceTemplate selectedDeviceTemplate;
	private DeviceManufacturer mf;

	/**
	 *
	 */
	public DeviceTemplateChooser(DeviceManufacturer mf) {
		this.mf = mf;
	}

	public void open(DeviceTemplateClosedListener l) {
		this.listener = l;
		skinnedDialog = new SkinnedDialog("skin3_dlg_deviceadd_mfchooser",
				"shell", SWT.TITLE | SWT.BORDER);

		skinnedDialog.addCloseListener(new SkinnedDialogClosedListener() {
			@Override
			public void skinDialogClosed(SkinnedDialog dialog) {
				if (listener != null) {
					listener.deviceTemplateChooserClosed(selectedDeviceTemplate);
				}
			}
		});

		SWTSkin skin = skinnedDialog.getSkin();
		SWTSkinObject so= skin.getSkinObject("list");
		if (so instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer soList = (SWTSkinObjectContainer) so;

			createDeviceTemplateList2(soList);
		}

		skinnedDialog.open();
	}

	private void createDeviceTemplateList2(SWTSkinObjectContainer soList) {
		DeviceTemplate[] devices = mf.getDeviceTemplates();

		if (devices.length == 0) {
			noDevices();
			return;
		}

		Arrays.sort(devices, new Comparator<DeviceTemplate>() {
			@Override
			public int compare(DeviceTemplate o1, DeviceTemplate o2) {
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});

		Composite parent = soList.getComposite();
		if (parent.getChildren().length > 0) {
			Utils.disposeComposite(parent, false);
		}

		SWTSkin skin = skinnedDialog.getSkin();

		RowLayout layout = new RowLayout(SWT.HORIZONTAL);
		layout.spacing = 0;
		layout.marginLeft = layout.marginRight = 0;
		layout.wrap = true;
		layout.justify = true;
		layout.fill = true;
		parent.setLayout(layout);


		Listener clickListener = new Listener() {
			boolean down = false;

			@Override
			public void handleEvent(Event event) {
				if (event.type == SWT.MouseDown) {
					down = true;
				} else if (event.type == SWT.MouseUp && down) {
					Widget widget = (event.widget instanceof Label)
							? ((Label) event.widget).getParent() : event.widget;
					selectedDeviceTemplate = (DeviceTemplate) widget.getData("obj");
					if (selectedDeviceTemplate == null) {
						Debug.out("selectedDeviceTemplate is null!");
					}
					skinnedDialog.close();
					down = false;
				}
			}
		};

		for (DeviceTemplate deviceTemplate : devices) {
			if (deviceTemplate.isAuto()) {
				continue;
			}
			String iconURL = null; // deviceTemplate.getIconURL();
			TranscodeChooser.addImageBox(parent, clickListener, null, deviceTemplate,
					iconURL, deviceTemplate.getName());
		}

		SWTSkinObjectText soTitle = (SWTSkinObjectText) skin.getSkinObject("title");
		if (soTitle != null) {
			soTitle.setTextID("devices.choose.device.title");
		}

		SWTSkinObjectText soSubTitle = (SWTSkinObjectText) skin.getSkinObject("subtitle");
		if (soSubTitle != null) {
			soSubTitle.setTextID("label.clickone");
		}

		Shell shell = skinnedDialog.getShell();
		Point computeSize = shell.computeSize(shell.getSize().x, SWT.DEFAULT, true);
		shell.setSize(computeSize);
		UIFunctionsSWT uiFunctionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctionsSWT != null) {
			Shell mainShell = uiFunctionsSWT.getMainShell();
			Utils.centerWindowRelativeTo(shell, mainShell);
		}
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	private void noDevices() {
		new MessageBoxShell(
				SWT.OK,
				"No Devices Found",
				"We couldn't find any devices.  Maybe you didn't install the Vuze Transcoder Plugin?").open(null);
		skinnedDialog.close();
	}

	public interface DeviceTemplateClosedListener {
		void deviceTemplateChooserClosed(DeviceTemplate deviceTemplate);
	}
}
