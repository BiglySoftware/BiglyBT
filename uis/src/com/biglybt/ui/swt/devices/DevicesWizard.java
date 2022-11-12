/*
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

package com.biglybt.ui.swt.devices;


import com.biglybt.core.devices.Device;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;


import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;


import com.biglybt.core.devices.DeviceTemplate;
import com.biglybt.ui.swt.imageloader.ImageLoader;

public class
DevicesWizard
{
	DeviceManagerUI		device_manager_ui;

	private Display display;

	Shell shell;


	Font boldFont;
	Font titleFont;
	Font subTitleFont;
	Font textInputFont;


	ImageLoader imageLoader;


	public
	DevicesWizard(
		DeviceManagerUI		dm_ui )
	{
		device_manager_ui	= dm_ui;

		imageLoader = ImageLoader.getInstance();

		shell = ShellFactory.createMainShell(SWT.TITLE | SWT.CLOSE | SWT.ICON
				| SWT.RESIZE);

		shell.setSize(650,400);

		Utils.centreWindow(shell);

		shell.setMinimumSize(550,400);

		display = shell.getDisplay();

		Utils.setShellIcon(shell);


		createFonts();

		shell.setText(MessageText.getString("wizard.device.title"));

		shell.addListener(SWT.Dispose, new Listener() {
			@Override
			public void handleEvent(Event event) {

				imageLoader.releaseImage("wizard_header_bg");

				if(titleFont != null && !titleFont.isDisposed()) {
					titleFont.dispose();
				}

				if(textInputFont != null && !textInputFont.isDisposed()) {
					textInputFont.dispose();
				}

				if(boldFont != null && !boldFont.isDisposed()) {
					boldFont.dispose();
				}

				if(subTitleFont != null && !subTitleFont.isDisposed()) {
					subTitleFont.dispose();
				}
			}
		});

		Composite header = new Composite(shell, SWT.NONE);
		header.setBackgroundImage(imageLoader.getImage("wizard_header_bg"));
		Label topSeparator = new Label(shell,SWT.SEPARATOR |SWT.HORIZONTAL);
		Composite main = new Composite(shell, SWT.NONE);
		Label bottomSeparator = new Label(shell,SWT.SEPARATOR |SWT.HORIZONTAL);
		Composite footer = new Composite(shell, SWT.NONE);

		FormLayout layout = new FormLayout();
		shell.setLayout(layout);

		FormData data;

		data = new FormData();
		data.top = new FormAttachment(0,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		//data.height = 50;
		header.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(header,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		topSeparator.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(topSeparator,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(bottomSeparator,0);
		main.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		data.bottom = new FormAttachment(footer,0);
		bottomSeparator.setLayoutData(data);

		data = new FormData();
		data.bottom = new FormAttachment(100,0);
		data.left = new FormAttachment(0,0);
		data.right = new FormAttachment(100,0);
		footer.setLayoutData(data);

		populateHeader(header);
		populateFooter(footer);

		shell.layout();
		shell.open();
	}

	private void
	populateHeader(
		Composite header)
	{
		header.setBackground(Colors.getSystemColor(display, SWT.COLOR_WHITE));

		Label title = new Label(header, SWT.WRAP);

		title.setFont(titleFont);

		title.setText( MessageText.getString("device.wizard.header") );

		FillLayout layout = new FillLayout();

		layout.marginHeight = 10;

		layout.marginWidth = 10;

		header.setLayout(layout);
	}





	private void
	createFonts()
	{
		FontData[] fDatas = shell.getFont().getFontData();

		for (FontData fData : fDatas) {
			fData.setStyle(SWT.BOLD);
		}
		boldFont = new Font(display,fDatas);


		for (FontData fData : fDatas) {
			if (Constants.isOSX) {
				fData.setHeight(12);
			} else {
				fData.setHeight(10);
			}
		}
		subTitleFont = new Font(display,fDatas);

		for (FontData fData : fDatas) {
			if (Constants.isOSX) {
				fData.setHeight(17);
			} else {
				fData.setHeight(14);
			}
		}
		titleFont = new Font(display,fDatas);


		for (FontData fData : fDatas) {
			if (Constants.isOSX) {
				fData.setHeight(14);
			} else {
				fData.setHeight(12);
			}
			fData.setStyle(SWT.NONE);
		}
		textInputFont = new Font(display,fDatas);
	}

	private void
	populateFooter(
		Composite footer)
	{
		Button cancelButton;
		Button createButton;

		cancelButton = new Button(footer,SWT.PUSH);
		cancelButton.setText(MessageText.getString("Button.cancel"));

		createButton = new Button(footer,SWT.PUSH);
		createButton.setText(MessageText.getString("device.wizard.create"));


		FormLayout layout = new FormLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		layout.spacing = 5;

		footer.setLayout(layout);
		FormData data;

		data = new FormData();
		data.right = new FormAttachment(100);
		data.width = 100;
		cancelButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.width = 175;
		createButton.setLayoutData(data);



		createButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {

				try{
					DeviceTemplate[] templates = device_manager_ui.getDeviceManager().getDeviceTemplates( Device.DT_MEDIA_RENDERER );

					for ( DeviceTemplate template: templates ){

						if ( !template.isAuto()){

							Device device = template.createInstance( template.getName() + " test!" );

							device.requestAttention();

							break;
						}
					}

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}
			}
		});

		cancelButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event arg0) {
				shell.close();
			}
		});
	}
}
