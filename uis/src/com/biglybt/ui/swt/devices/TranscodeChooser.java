/*
 * Created on Mar 1, 2009
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

package com.biglybt.ui.swt.devices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.devices.Device;
import com.biglybt.core.devices.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.skin.*;
import com.biglybt.ui.swt.utils.ColorCache;
import com.biglybt.ui.swt.utils.FontUtils;
import com.biglybt.ui.swt.views.skin.SkinnedDialog;

/**
 * @author TuxPaper
 * @created Mar 1, 2009
 *
 */
public abstract class TranscodeChooser
{
	private static final String skinFile = "skin3_transcodechooser";

	private static final String shellSkinObjectID = "shell";

	private Shell shell;

	private SWTSkin skin;

	private Font fontDevice;

	protected TranscodeTarget selectedTranscodeTarget;

	protected TranscodeProfile selectedProfile;

	protected DeviceTemplate selectedDeviceTemplate;

	private SWTSkinObjectContainer soList;

	private Shell mainShell;

	private SWTSkinObjectContainer soBottomContainer;

	private Button btnNoPrompt;

	private int transcodeRequirement = TranscodeTarget.TRANSCODE_NEVER;

	private java.util.List<String> listImageIDsToRelease = new ArrayList<>();

	private SWTSkinObjectText soInfoTitle;

	private SWTSkinObjectText soInfoText;

	private Font fontDeviceDesc;

	private TranscodeProfile[] transcodeProfiles;

	public TranscodeChooser() {
		this((TranscodeTarget) null);
	}

	public TranscodeChooser(TranscodeTarget tt) {
		selectedTranscodeTarget = tt;
		
		if ( selectedTranscodeTarget != null ){
			transcodeRequirement = selectedTranscodeTarget.getTranscodeRequirement();
		}
	}

	public TranscodeChooser(TranscodeProfile[] transcodeProfiles) {
		this.transcodeProfiles = transcodeProfiles;
	}

	public void show( Runnable fire_on_install ) {
		// Check if plugin is installed
		if (!DevicesFTUX.ensureInstalled( fire_on_install )) {
			return;
		}

		mainShell = UIFunctionsManagerSWT.getUIFunctionsSWT().getMainShell();
		shell = ShellFactory.createShell(mainShell, SWT.DIALOG_TRIM | SWT.RESIZE);

		Utils.setShellIcon(shell);

		skin = SWTSkinFactory.getNonPersistentInstance(
				SkinnedDialog.class.getClassLoader(), "com/biglybt/ui/skin/",
				skinFile + ".properties");

		skin.initialize(shell, shellSkinObjectID);

		shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.close();
				}
			}
		});

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				closed();
			}
		});

		skin.layout();

		SWTSkinObject soBottom = skin.getSkinObject("bottom");
		if (soBottom instanceof SWTSkinObjectContainer) {
			soBottomContainer = (SWTSkinObjectContainer) soBottom;

			soBottomContainer.addListener(new SWTSkinObjectListener() {

				@Override
				public Object eventOccured(SWTSkinObject skinObject, int eventType,
				                           Object params) {
					if (eventType == EVENT_SHOW) {
						skinObject.removeListener(this);
						initBottom();
					}
					return null;
				}
			});
			soBottomContainer.setVisible(selectedTranscodeTarget != null);
		}

		soList = (SWTSkinObjectContainer) skin.getSkinObject("list");
		if (soList != null) {
			if (transcodeProfiles != null) {
				createProfileList(soList);
			} else if (selectedTranscodeTarget == null) {
				createDeviceList(soList);
			} else {
				transcodeProfiles = selectedTranscodeTarget.getTranscodeProfiles();
				createProfileList(soList);
			}
		}

		// we may have disposed of shell during device/profile list building
		// (ex. no devices avail)
		if (shell.isDisposed()) {
			return;
		}

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				Utils.disposeSWTObjects(new Object[] {
					fontDevice,
					fontDeviceDesc
				});
				for (String id : listImageIDsToRelease) {
					ImageLoader.getInstance().releaseImage(id);
				}
			}
		});
		Utils.verifyShellRect(shell, true);
		shell.open();
	}

	/**
	 * @param soBottomContainer2
	 *
	 * @since 4.1.0.5
	 */
	protected void initBottom() {
		Composite composite = soBottomContainer.getComposite();
		btnNoPrompt = new Button(composite, SWT.CHECK);
		Messages.setLanguageText(btnNoPrompt, "option.rememberthis");

		Label lblXCode = new Label(composite, SWT.NONE);
		lblXCode.setText(MessageText.getString("device.xcode"));

		final Combo cmbXCode = new Combo(composite, SWT.DROP_DOWN | SWT.READ_ONLY);

		cmbXCode.add(MessageText.getString("device.xcode.whenreq"));
		cmbXCode.add(MessageText.getString("device.xcode.always"));
		cmbXCode.add(MessageText.getString("device.xcode.never"));
		transcodeRequirement = selectedTranscodeTarget.getTranscodeRequirement();
		switch (transcodeRequirement) {
			case TranscodeTarget.TRANSCODE_ALWAYS:
				cmbXCode.select(1);
				break;

			case TranscodeTarget.TRANSCODE_NEVER:
				cmbXCode.select(2);
				break;

			case TranscodeTarget.TRANSCODE_WHEN_REQUIRED:
			default:
				cmbXCode.select(0);
				break;
		}

		cmbXCode.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int i = cmbXCode.getSelectionIndex();
				switch (i) {
					case 0:
						transcodeRequirement = TranscodeTarget.TRANSCODE_WHEN_REQUIRED;
						break;

					case 1:
						transcodeRequirement = TranscodeTarget.TRANSCODE_ALWAYS;
						break;

					case 2:
						transcodeRequirement = TranscodeTarget.TRANSCODE_NEVER;
						break;
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		FormData fd;

		fd = new FormData();
		fd.left = new FormAttachment(0, 10);
		fd.top = new FormAttachment(cmbXCode, 0, SWT.CENTER);
		btnNoPrompt.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(100, -10);
		fd.top = new FormAttachment(0, 5);
		fd.bottom = new FormAttachment(100, -5);
		cmbXCode.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(cmbXCode, -5);
		fd.top = new FormAttachment(cmbXCode, 0, SWT.CENTER);
		lblXCode.setLayoutData(fd);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode == 0) {
			lblXCode.setVisible(false);
			cmbXCode.setVisible(false);
		}

		Point computeSize = shell.computeSize(shell.getClientArea().width,
				SWT.DEFAULT, true);
		shell.setSize(computeSize);
	}

	/**
	 * @param soList
	 *
	 * @since 4.1.0.5
	 */
	private void createProfileList(SWTSkinObjectContainer soList) {
		if (selectedTranscodeTarget == null && selectedDeviceTemplate == null) {
			new MessageBoxShell(SWT.OK, "No Device", "No Device Selected!?").open(null);
			shell.dispose();
			return;
		}

		if (selectedTranscodeTarget != null) {
			try {
				TranscodeProfile defaultProfile = selectedTranscodeTarget.getDefaultTranscodeProfile();
				if (defaultProfile != null) {
					// user chose not to ask

					if ( selectedTranscodeTarget.getTranscodeRequirement() == TranscodeTarget.TRANSCODE_NEVER ){
							// take note of never-xcode override
						selectedProfile = selectedTranscodeTarget.getBlankProfile();
					}else{
						selectedProfile = defaultProfile;
					}
					shell.dispose();
					return;
				}
			} catch (TranscodeException e) {
			}
		}

		if (transcodeProfiles.length == 0 || ( selectedTranscodeTarget != null && selectedTranscodeTarget.getTranscodeRequirement() == TranscodeTarget.TRANSCODE_NEVER )){
			if ( selectedTranscodeTarget != null ){
				selectedProfile = selectedTranscodeTarget.getBlankProfile();
				shell.dispose();
				return;
			}
			new MessageBoxShell(SWT.OK, "No Profiles", "No Profiles for device!" ).open(null);
			shell.dispose();
			return;
		}

		if (transcodeProfiles.length == 1) {
			selectedProfile = transcodeProfiles[0];

			shell.dispose();
			return;
		}

		Arrays.sort(transcodeProfiles, new Comparator<TranscodeProfile>() {
			@Override
			public int compare(TranscodeProfile o1, TranscodeProfile o2) {
				int i1 = o1.getIconIndex();
				int i2 = o2.getIconIndex();

				if ( i1 == i2 ){

					return o1.getName().compareToIgnoreCase(o2.getName());
				}else{

					return( i1 - i2 );
				}
			}
		});

		Composite parent = soList.getComposite();
		if (parent.getChildren().length > 0) {
			Utils.disposeComposite(parent, false);
		}

		soInfoTitle = (SWTSkinObjectText) skin.getSkinObject("info-title");
		soInfoText = (SWTSkinObjectText) skin.getSkinObject("info-text");
		resetProfileInfoBox(false);

		RowLayout layout = new RowLayout(SWT.HORIZONTAL);
		layout.spacing = 0;
		layout.marginLeft = layout.marginRight = 0;
		layout.wrap = true;
		layout.justify = true;
		layout.fill = true;
		parent.setLayout(layout);

		Listener listenerMouseInout = new Listener() {
			@Override
			public void handleEvent(Event event) {
				Widget widget = (event.widget instanceof Canvas)
						? ((Canvas) event.widget).getParent() : event.widget;

				Composite c = TranscodeChooser.this.soList.getComposite();
				Rectangle bounds = c.getClientArea();
				c.redraw(bounds.x, bounds.y, bounds.width, bounds.height, true);

				TranscodeProfile profile = (TranscodeProfile) widget.getData("obj");
				if (profile == null) {
					return;
				}
				if (event.type == SWT.MouseEnter) {
					String description = profile.getDescription();
					if (selectedTranscodeTarget != null) {
						if (profile == selectedTranscodeTarget.getBlankProfile()) {
							description = null;
						}
					}

					if (description == null || description.length() == 0) {
						resetProfileInfoBox(true);
					} else {
						if (soInfoTitle != null) {
							soInfoTitle.setTextID(
									"devices.choose.profile.info.title.selected", new String[] {
										profile.getName()
									});
						}
						if (soInfoText != null) {
							soInfoText.setText(description);
							Point computeSize = shell.computeSize(
									shell.getClientArea().width, SWT.DEFAULT, true);
							if (computeSize.y > shell.getSize().y) {
								shell.setSize(computeSize);
							}
						}
					}
				}
			}
		};

		parent.addListener(SWT.MouseEnter, new Listener() {
			@Override
			public void handleEvent(Event event) {
				resetProfileInfoBox(true);
			}
		});

		Listener clickListener = new Listener() {
			boolean down = false;

			@Override
			public void handleEvent(Event event) {
				if (event.type == SWT.MouseDown) {
					down = true;
				} else if (event.type == SWT.MouseUp && down) {
					Widget widget = (event.widget instanceof Label)
							? ((Label) event.widget).getParent() : event.widget;
					selectedProfile = (TranscodeProfile) widget.getData("obj");
					if (selectedTranscodeTarget != null && selectedProfile == selectedTranscodeTarget.getBlankProfile()) {
						transcodeRequirement = TranscodeTarget.TRANSCODE_NEVER;
					}
					if (selectedProfile == null) {
						Debug.out("profile is null!");
					} else {
						if (btnNoPrompt != null) {
							if (btnNoPrompt.getSelection()) {
								if ( transcodeRequirement == TranscodeTarget.TRANSCODE_NEVER ){
									selectedTranscodeTarget.setTranscodeRequirement( transcodeRequirement );
								}else{
									selectedTranscodeTarget.setDefaultTranscodeProfile(selectedProfile);
									selectedTranscodeTarget.setTranscodeRequirement( transcodeRequirement );
								}
							}
						}
					}
					shell.dispose();
					down = false;
				}
			}
		};

		int	total_images = 0;

		for (TranscodeProfile profile : transcodeProfiles) {
			addImageBox(parent, clickListener, listenerMouseInout, profile,
					profile.getIconURL(), profile.getName());

			total_images++;
		}
		if (selectedTranscodeTarget != null) {
			addImageBox(parent, clickListener, listenerMouseInout,
					selectedTranscodeTarget.getBlankProfile(), "", "Do not transcode");

			total_images++;
		}
		SWTSkinObjectText soTitle = (SWTSkinObjectText) skin.getSkinObject("title");
		if (soTitle != null) {
			soTitle.setTextID("devices.choose.profile.title");
		}

		SWTSkinObjectText soSubTitle = (SWTSkinObjectText) skin.getSkinObject("subtitle");
		if (soSubTitle != null) {
			soSubTitle.setTextID("label.clickone");
		}

		if (soBottomContainer != null) {
			soBottomContainer.setVisible(true);
		}

		SWTSkinObjectContainer soButtonBottomArea = (SWTSkinObjectContainer) skin.getSkinObject("button-bottom");
		if (soButtonBottomArea != null) {
			soButtonBottomArea.setVisible(false);
		}

			// once we get to 13 icons (e.g. for iTunes now we have ipad4/ipad mini we increase the width)
			// to ensure the dialog doesn't get too long

		Point computeSize = shell.computeSize(total_images>12?800:600, SWT.DEFAULT, true);
		shell.setSize(computeSize);
		Utils.centerWindowRelativeTo(shell, mainShell);
	}

	/**
	 *
	 *
	 * @since 4.1.0.5
	 */
	public static void addImageBox(Composite parent, Listener clickListener,
			Listener listenerMouseInout, Object obj, String iconURL, String name) {
		GridData gridData;
		final Shell shell = parent.getShell();
		final Composite c = new Composite(parent, SWT.NONE);
		GridLayout clayout = new GridLayout();
		clayout.marginWidth = clayout.horizontalSpacing = 0;
		c.setLayout(clayout);
		c.setCursor(c.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		c.addListener(SWT.MouseUp, clickListener);
		c.addListener(SWT.MouseDown, clickListener);
		c.setData("obj", obj);

		if (listenerMouseInout != null) {
			c.addListener(SWT.MouseEnter, listenerMouseInout);
			c.addListener(SWT.MouseExit, listenerMouseInout);
		}

		final Canvas lblImage = new Canvas(c, SWT.DOUBLE_BUFFERED);
		if (listenerMouseInout != null) {
			lblImage.addListener(SWT.MouseEnter, listenerMouseInout);
			lblImage.addListener(SWT.MouseExit, listenerMouseInout);
		}
		lblImage.addListener(SWT.MouseUp, clickListener);
		lblImage.addListener(SWT.MouseDown, clickListener);
		lblImage.setData("obj", obj);
		lblImage.addListener(SWT.Paint, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Rectangle area = lblImage.getBounds();
				Rectangle carea = c.getBounds();

				Point ptInDisplay = c.toDisplay(0, 0);

				event.gc.setAdvanced(true);
				event.gc.setAntialias(SWT.ON);
				event.gc.setLineWidth(2);

				if (new Rectangle(ptInDisplay.x, ptInDisplay.y, carea.width,
						carea.height).contains(event.display.getCursorLocation())) {
					//if (event.display.getCursorControl() == lblImage) {

					Color color1 = ColorCache.getColor(event.gc.getDevice(), 252, 253,
							255);
					Color color2 = ColorCache.getColor(event.gc.getDevice(), 169, 195,
							252);
					Pattern pattern = new Pattern(event.gc.getDevice(), 0, 0, 0,
							area.height, color1, 0, color2, 200);
					event.gc.setBackgroundPattern(pattern);

					event.gc.fillRoundRectangle(0, 0, area.width - 1, area.height - 1,
							20, 20);

					event.gc.setBackgroundPattern(null);
					pattern.dispose();

					pattern = new Pattern(event.gc.getDevice(), 0, 0, 0, area.height,
							color2, 50, color2, 255);
					event.gc.setForegroundPattern(pattern);

					event.gc.drawRoundRectangle(0, 0, area.width - 1, area.height - 1,
							20, 20);

					event.gc.setForegroundPattern(null);
					pattern.dispose();
				}

				Image image = (Image) lblImage.getData("Image");
				if (image != null) {
					Rectangle bounds = image.getBounds();
					event.gc.drawImage(image, bounds.x, bounds.y, bounds.width,
							bounds.height, 8, 5, bounds.width, bounds.height);
				} else {
					Rectangle ca = lblImage.getClientArea();
					event.gc.setAdvanced(true);
					event.gc.setAntialias(SWT.ON);
					event.gc.setAlpha(50);
					event.gc.setBackground(event.gc.getForeground());
					event.gc.fillRoundRectangle(ca.x + 10, ca.y + 5, ca.width - 21,
							ca.height - 11, 20, 20);
				}
			}
		});
		gridData = new GridData(GridData.FILL_VERTICAL);
		gridData.heightHint = 50;
		gridData.widthHint = 100;
		if (iconURL != null && iconURL.length() > 0) {
			ImageLoader imageLoader = ImageLoader.getInstance();
			Image image = imageLoader.getUrlImage(iconURL,
					new ImageLoader.ImageDownloaderListener() {
						@Override
						public void imageDownloaded(Image image, String key, boolean returnedImmediately) {
							if (!returnedImmediately) {
								if (lblImage.isDisposed()) {
									return;
								}
								lblImage.setData("Image", image);
								Rectangle bounds = image.getBounds();
								GridData gridData = (GridData) lblImage.getLayoutData();
								gridData.heightHint = bounds.height + 10;
								gridData.widthHint = bounds.width + 16;
								lblImage.setLayoutData(gridData);
								lblImage.getShell().layout(new Control[] {
									lblImage
								});
								Point computeSize = shell.computeSize(600, SWT.DEFAULT, true);
								shell.setSize(computeSize);
							}
						}
					});
			if (image != null) {
				lblImage.setData("Image", image);
				Rectangle bounds = image.getBounds();
				gridData.heightHint = bounds.height + 10;
				gridData.widthHint = bounds.width + 16;
			}
		}
		lblImage.setLayoutData(gridData);

		Label label = new Label(c, SWT.WRAP | SWT.CENTER);
		if (listenerMouseInout != null) {
			label.addListener(SWT.MouseEnter, listenerMouseInout);
		}
		label.addListener(SWT.MouseUp, clickListener);
		label.addListener(SWT.MouseDown, clickListener);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		label.setLayoutData(gridData);
		String s = name;
		//s += " (via " + profile.getProvider().getName() + ")";
		label.setText(s);
		label.setCursor(c.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
	}

	/**
	 *
	 *
	 * @param layout
	 * @since 4.1.0.5
	 */
	protected void resetProfileInfoBox(boolean layout) {
		if (soInfoTitle != null) {
			soInfoTitle.setTextID("devices.choose.profile.info.title");
		}
		if (soInfoText != null) {
			soInfoText.setTextID("devices.choose.profile.info.text");
			if (layout) {
				Point computeSize = shell.computeSize(shell.getClientArea().width,
						SWT.DEFAULT, true);
				shell.setSize(computeSize);
			}
		}
	}

	private void createDeviceList(SWTSkinObjectContainer soDeviceList) {
		Composite parent = soDeviceList.getComposite();
		FormLayout layout = new FormLayout();
		layout.marginLeft = 10;
		layout.marginHeight = 15;
		parent.setLayout(layout);

		DeviceManager device_manager = DeviceManagerFactory.getSingleton();
		Device[] devices = device_manager.getDevices();

		if (devices.length == 0) {
			noDevices();
			return;
		}

		Arrays.sort(devices, new Comparator<Device>() {
			@Override
			public int compare(Device o1, Device o2) {
				return o1.getName().compareToIgnoreCase(o2.getName());
			}
		});

		Font baseFont = parent.getFont();
		fontDevice = FontUtils.getFontWithStyle(baseFont, SWT.BOLD, 1.1f);
		fontDeviceDesc = FontUtils.getFontWithStyle(baseFont, SWT.NONE, 1.1f);

		/**
		PaintListener paintListener = new PaintListener() {
			public void paintControl(PaintEvent e) {
				Rectangle ca = ((Composite) e.widget).getClientArea();
				e.gc.setForeground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_NORMAL_SHADOW));
				e.gc.setBackground(Colors.getSystemColor(e.display, SWT.COLOR_WIDGET_LIGHT_SHADOW));
				e.gc.setAntialias(SWT.ON);
				e.gc.fillRoundRectangle(ca.x, ca.y, ca.width - 1, ca.height - 1, 10, 10);
				e.gc.drawRoundRectangle(ca.x, ca.y, ca.width - 1, ca.height - 1, 10, 10);
			}
		};
		**/

		boolean hide_generic = COConfigurationManager.getBooleanParameter(
				DeviceManagerUI.CONFIG_VIEW_HIDE_REND_GENERIC, true);

		boolean show_only_tagged = COConfigurationManager.getBooleanParameter(
				DeviceManagerUI.CONFIG_VIEW_SHOW_ONLY_TAGGED, false);

		int numDevices = 0;
		Button lastButton = null;
		for (Device device : devices) {
			if (device.getType() != Device.DT_MEDIA_RENDERER || device.isHidden()
					|| !(device instanceof DeviceMediaRenderer)) {
				continue;
			}

			DeviceMediaRenderer renderer = (DeviceMediaRenderer) device;

			if (hide_generic && renderer.isNonSimple()) {
				continue;
			}

			if ( show_only_tagged && !renderer.isTagged()){
				continue;
			}

			TranscodeTarget transcodeTarget = (TranscodeTarget) device;

			if (transcodeTarget.getTranscodeProfiles().length == 0) {

				if ( transcodeTarget.getTranscodeRequirement() != TranscodeTarget.TRANSCODE_NEVER ){

					continue;
				}
			}

			String imageID = "image.sidebar.device." + DeviceManagerUI.getDeviceImageIDs( device )[0] + ".big";

			lastButton = createDeviceButton(parent, device, device.getName(),
					device.getShortDescription(), imageID, lastButton);
			numDevices++;
		}

		if (numDevices == 0) {
			noDevices();
			return;
		}

		SWTSkinObjectText soTitle = (SWTSkinObjectText) skin.getSkinObject("title");
		if (soTitle != null) {
			soTitle.setTextID("devices.choose.device.title");
		}

		SWTSkinObjectText soSubTitle = (SWTSkinObjectText) skin.getSkinObject("subtitle");
		if (soSubTitle != null) {
			soSubTitle.setText("");
		}

		SWTSkinObjectContainer soButtonBottomArea = (SWTSkinObjectContainer) skin.getSkinObject("button-bottom");
		if (soButtonBottomArea != null) {
			soButtonBottomArea.setVisible(true);

			SWTSkinObjectButton soOk = (SWTSkinObjectButton) skin.getSkinObject("ok");
			if (soOk != null) {
				shell.setDefaultButton(soOk.getButton());
				soOk.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						transcodeProfiles = selectedTranscodeTarget.getTranscodeProfiles();
						createProfileList(soList);
					}
				});
			}

			SWTSkinObjectButton soCancel = (SWTSkinObjectButton) skin.getSkinObject("cancel");
			if (soCancel != null) {
				soCancel.addSelectionListener(new SWTSkinButtonUtility.ButtonListenerAdapter() {
					@Override
					public void pressed(SWTSkinButtonUtility buttonUtility,
					                    SWTSkinObject skinObject, int stateMask) {
						shell.close();
					}
				});
			}
		}

		if (soBottomContainer != null) {
			soBottomContainer.setVisible(false);
		}

		//shell.pack();
		Point computeSize = shell.computeSize(400, SWT.DEFAULT, true);
		shell.setSize(computeSize);
		shell.layout(true);
		Utils.centerWindowRelativeTo(shell, mainShell);
	}

	private Button createDeviceButton(Composite parent, Object deviceObj,
			String name, String shortDescription, String imageID, Button lastButton) {
		Button button = new Button(parent, SWT.LEFT | SWT.RADIO);
		StringBuilder sb = new StringBuilder(name);
		button.setFont(fontDevice);
		button.setData("Device", deviceObj);
		button.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				Object device = e.widget.getData("Device");
				if (device instanceof TranscodeTarget) {
					selectedTranscodeTarget = (TranscodeTarget) device;
				} else if (device instanceof DeviceTemplate) {
					selectedDeviceTemplate = (DeviceTemplate) device;
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		if (lastButton == null) {
			button.setSelection(true);
			if (deviceObj instanceof TranscodeTarget) {
				selectedTranscodeTarget = (TranscodeTarget) deviceObj;
			}
		}

		Image imgRenderer = null;
		if (imageID != null) {
			listImageIDsToRelease.add(imageID);
			imgRenderer = ImageLoader.getInstance().getImage(imageID);
		}

		if (ImageLoader.isRealImage(imgRenderer)) {
			button.setImage(imgRenderer);

			// buttons are center when they have an image..
			// fill with a bunch of spaces so it left aligns
			//char[] c = new char[100];
			//Arrays.fill(c, ' ');
			//sb.append(c);
		} else {
			sb.insert(0, ' ');
		}

		button.setText(sb.toString());

		FormData fd = new FormData();
		fd.left = new FormAttachment(0, 0);
		if (lastButton == null) {
			fd.top = new FormAttachment(0, 0);
		} else {
			fd.top = new FormAttachment(lastButton, 15);
		}
		button.setLayoutData(fd);

		if (shortDescription != null && shortDescription.length() > 0) {
			Label label = new Label(parent, SWT.None);
			label.setText("(" + shortDescription + ")");

			fd = new FormData();
			fd.top = new FormAttachment(button, 0, SWT.CENTER);
			fd.left = new FormAttachment(button, 5);
			label.setLayoutData(fd);
		}

		return button;
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
		shell.dispose();
	}

	public abstract void closed();

	public int getTranscodeRequirement() {
		return transcodeRequirement;
	}
}
