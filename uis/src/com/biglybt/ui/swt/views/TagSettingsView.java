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

package com.biglybt.ui.swt.views;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.ColorParameter;
import com.biglybt.ui.swt.config.generic.GenericBooleanParameter;
import com.biglybt.ui.swt.config.generic.GenericFloatParameter;
import com.biglybt.ui.swt.config.generic.GenericIntParameter;
import com.biglybt.ui.swt.config.generic.GenericParameterAdapter;
import com.biglybt.ui.swt.config.generic.GenericStringListParameter;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import org.gudy.bouncycastle.util.Arrays;

import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.utils.FontUtils;

/**
 * @author TuxPaper
 * @created Mar 11, 2015
 *
 */
public class TagSettingsView
	implements UISWTViewCoreEventListener, TagTypeListener
{
	private static final String CM_ADD_REMOVE 	= "am=0;";
	private static final String CM_ADD_ONLY	 	= "am=1;";
	private static final String CM_REMOVE_ONLY	= "am=2;";

	private UISWTView swtView;

	private Composite cMainComposite;

	private ScrolledComposite sc;

	private Tag[] tags;

	public static class Params
	{
		private Control cName;

		private ColorParameter tagColor;

		private GenericIntParameter maxDownloadSpeed;

		private GenericIntParameter maxUploadSpeed;

		private GenericBooleanParameter viewInSideBar;

		private GenericBooleanParameter isPublic;

		public GenericBooleanParameter uploadPriority;

		public GenericFloatParameter min_sr;

		public GenericFloatParameter max_sr;

		public GenericStringListParameter	max_sr_action;

		public GenericFloatParameter max_aggregate_sr;

		public GenericStringListParameter	max_aggregate_sr_action;

		public GenericBooleanParameter	max_aggregate_sr_priority;

		public folderOption 			initalSaveFolder;
		public GenericBooleanParameter	initalSaveData;
		public GenericBooleanParameter	initalSaveTorrent;

		public folderOption 			moveOnCompleteFolder;
		public GenericBooleanParameter	moveOnCompleteData;
		public GenericBooleanParameter	moveOnCompleteTorrent;

		public folderOption 			copyOnCompleteFolder;
		public GenericBooleanParameter	copyOnCompleteData;
		public GenericBooleanParameter	copyOnCompleteTorrent;

		public Text constraints;
		public Label	constraintError;
		
		private GenericStringListParameter constraintMode;

		public GenericIntParameter tfl_max_taggables;

		public GenericStringListParameter	tfl_removal_policy;

		public GenericStringListParameter	tfl_ordering;

		public GenericBooleanParameter	notification_post_add;

		public GenericBooleanParameter	notification_post_remove;
	}

	private Params params = null;

	private Button btnSaveConstraint;

	private Button btnResetConstraint;

	public TagSettingsView() {
	}

	// @see com.biglybt.ui.swt.pif.UISWTViewEventListener#eventOccurred(com.biglybt.ui.swt.pif.UISWTViewEvent)
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite) event.getData());
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(cMainComposite);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				Object ds = event.getData();
				dataSourceChanged(ds);
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;
		}

		return true;
	}

	private void delete() {
		dataSourceChanged(null);
		params = null;
	}

	private void refresh() {
	}

	private void dataSourceChanged(Object ds) {

		if (tags != null) {
			for (Tag tag : tags) {
  			TagType tagType = tag.getTagType();
  			tagType.removeTagTypeListener(this);
			}
		}

		if (ds instanceof Tag) {
			tags = new Tag[] { (Tag) ds };
		} else if (ds instanceof Object[]) {
			Object[] objects = (Object[]) ds;
			if (objects[0] instanceof Tag) {
				tags = new Tag[objects.length];
				System.arraycopy(objects, 0, tags, 0, tags.length);
			} else {
				tags = null;
			}
		} else {
			tags = null;
		}

		if (tags != null) {
			for (Tag tag : tags) {
  			TagType tagType = tag.getTagType();
  			// Lazy way to ensure only one listener per type gets added
  			tagType.removeTagTypeListener(this);
  			tagType.addTagTypeListener(this, false);
			}
		}
		initialize(null);

	}

	private void initialize(final Composite parent) {
		Utils.execSWTThread(
			new Runnable()
			{
				@Override
				public void
				run()
				{
					swt_initialize( parent );
				}
			});
	}

	private void swt_initialize(Composite parent) {
		if (cMainComposite == null || cMainComposite.isDisposed()) {
			if (parent == null || parent.isDisposed()) {
				return;
			}
			sc = new ScrolledComposite(parent, SWT.V_SCROLL);
			sc.setExpandHorizontal(true);
			sc.setExpandVertical(true);
			sc.getVerticalBar().setIncrement(16);
			Layout parentLayout = parent.getLayout();
			if (parentLayout instanceof GridLayout) {
				GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
				Utils.setLayoutData(sc, gd);
			} else if (parentLayout instanceof FormLayout) {
				Utils.setLayoutData(sc, Utils.getFilledFormData());
			}

			cMainComposite = new Composite(sc, SWT.NONE);

			sc.setContent(cMainComposite);
		} else {
			Utils.disposeComposite(cMainComposite, false);
		}

		if (tags == null) {
			params = null;
			cMainComposite.setLayout(new FillLayout());
			Label label = new Label(cMainComposite, SWT.NONE);
			label.setText(MessageText.getString( "tag.settings.select.tag" ));
		} else {
			final int numTags = tags.length;


			int isTagVisible = -1;
			int canBePublic = -1;
			int[] tagColor = tags[0].getColor();
			boolean tagsAreTagFeatureRateLimit = true;
			Set<String> listTagTypes = new HashSet<>();
			for (Tag tag : tags) {
				TagType tt = tag.getTagType();
				String s = tt.getTagTypeName(true);
				listTagTypes.add(s);

				if (tagsAreTagFeatureRateLimit && !(tag instanceof TagFeatureRateLimit)) {
					tagsAreTagFeatureRateLimit = false;
				}

				isTagVisible = updateIntBoolean(tag.isVisible(), isTagVisible);
				canBePublic = updateIntBoolean(tag.canBePublic(), canBePublic);

				if (tagColor != null) {
  				int[] color = tag.getColor();
  				if (!Arrays.areEqual(tagColor, color)) {
  					tagColor = null;
  				}
				}
			}
			String tagTypes = GeneralUtils.stringJoin(listTagTypes, ", ");

			params = new Params();

			GridData gd;
			GridLayout gridLayout;
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			cMainComposite.setLayout(gridLayout);

			Composite cSection1 = new Composite(cMainComposite, SWT.NONE);
			gridLayout = new GridLayout(4, false);
			gridLayout.marginHeight = 0;
			cSection1.setLayout(gridLayout);
			gd = new GridData(SWT.FILL, SWT.FILL, true, false);
			cSection1.setLayoutData(gd);

			Composite cSection2 = new Composite(cMainComposite, SWT.NONE);
			gridLayout = new GridLayout(4, false);
			cSection2.setLayout(gridLayout);
			gd = new GridData(SWT.FILL, SWT.FILL, true, false);
			cSection2.setLayoutData(gd);

			Label label;

			// Field: Tag Type
			label = new Label(cSection1, SWT.NONE);
			FontUtils.setFontHeight(label, 12, SWT.BOLD);
			gd = new GridData();
			gd.horizontalSpan = 4;
			Utils.setLayoutData(label, gd);
			label.setText(tagTypes);

			// Field: Name
			label = new Label(cSection1, SWT.NONE);
			Messages.setLanguageText(label, "MinimizedWindow.name");
			gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
			Utils.setLayoutData(label, gd);

			if (numTags == 1 && !tags[0].getTagType().isTagTypeAuto()) {
				Text txtName = new Text(cSection1, SWT.BORDER);
				params.cName = txtName;
				gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
				Utils.setLayoutData(txtName, gd);

				txtName.addModifyListener(new ModifyListener() {
					@Override
					public void modifyText(ModifyEvent e) {
						try {
							String newName = ((Text) e.widget).getText();
							if (!tags[0].getTagName(true).equals(newName)) {
								tags[0].setTagName(newName);
							}
						} catch (TagException e1) {
							Debug.out(e1);
						}
					}
				});
			} else {
				label = new Label(cSection1, SWT.WRAP);
				gd = Utils.getWrappableLabelGridData(1, GridData.GRAB_HORIZONTAL);
				Utils.setLayoutData(label, gd);
				params.cName = label;
			}

			// Field: Color
			label = new Label(cSection1, SWT.NONE);
			Messages.setLanguageText(label, "label.color");
			if (tagColor == null) {
				tagColor = new int[] { 0, 0, 0 };
			}
			params.tagColor = new ColorParameter(cSection1, null, tagColor[0], tagColor[1],
					tagColor[2]) {
				// @see com.biglybt.ui.swt.config.ColorParameter#newColorChosen(org.eclipse.swt.graphics.RGB)
				@Override
				public void newColorChosen(RGB newColor) {
					for (Tag tag : tags) {
  					tag.setColor(new int[] {
  						newColor.red,
  						newColor.green,
  						newColor.blue
  					});
					}
				}
			};

			// Field: Visible

			params.viewInSideBar = new GenericBooleanParameter(
					new BooleanParameterAdapter() {
						@Override
						public Boolean getBooleanValue(String key) {
							int isTagVisible = -1;
							for (Tag tag : tags) {
								isTagVisible = updateIntBoolean(tag.isVisible(), isTagVisible);
							}
							return isTagVisible == 2 ? null : (isTagVisible == 1);
						}

						@Override
						public void setBooleanValue(String key, boolean value) {
							for (Tag tag : tags) {
								tag.setVisible(value);
							}
						}
					}, cSection2, null, "TagSettings.viewInSideBar");
			gd = new GridData();
			gd.horizontalSpan = 4;
			params.viewInSideBar.setLayoutData(gd);

			// Field: Public
			if (canBePublic == 1) {
				params.isPublic = new GenericBooleanParameter(
						new BooleanParameterAdapter() {
							@Override
							public Boolean getBooleanValue(String key) {
								int val = -1;
								for (Tag tag : tags) {
									val = updateIntBoolean(tag.isPublic(), val);
								}
								return val == 2 ? null : (val == 1);
							}

							@Override
							public void setBooleanValue(String key, boolean value) {
								for (Tag tag : tags) {
									tag.setPublic(value);
								}
							}
						}, cSection2, null, "TagAddWindow.public.checkbox");
				gd = new GridData();
				gd.horizontalSpan = 4;
				params.isPublic.setLayoutData(gd);
			}

			////////////////////

			Group gTransfer = new Group(cMainComposite, SWT.NONE);
			gTransfer.setText( MessageText.getString("label.transfer.settings"));
			gridLayout = new GridLayout(6, false);
			gTransfer.setLayout(gridLayout);

			gd = new GridData(SWT.FILL, SWT.NONE, false, false, 4, 1);
			gTransfer.setLayoutData(gd);

			if (tagsAreTagFeatureRateLimit) {
				final TagFeatureRateLimit rls[] = new TagFeatureRateLimit[tags.length];
				System.arraycopy(tags, 0, rls, 0, tags.length);

				boolean supportsTagDownloadLimit = true;
				boolean supportsTagUploadLimit = true;
				boolean hasTagUploadPriority = true;
				for (TagFeatureRateLimit rl : rls) {
					supportsTagDownloadLimit &= rl.supportsTagDownloadLimit();
					supportsTagUploadLimit &= rl.supportsTagUploadLimit();
					hasTagUploadPriority &= rl.getTagUploadPriority() >= 0;
				}

				String k_unit = DisplayFormatters.getRateUnitBase10(
						DisplayFormatters.UNIT_KB).trim();

				int	cols_used = 0;

				// Field: Download Limit
				if (supportsTagDownloadLimit) {

					gd = new GridData();
					label = new Label(gTransfer, SWT.NULL);
					Utils.setLayoutData(label, gd);
					label.setText(k_unit + " " + MessageText.getString(
							"GeneralView.label.maxdownloadspeed.tooltip"));

					gd = new GridData();
					//gd.horizontalSpan = 3;
					params.maxDownloadSpeed = new GenericIntParameter(
							new GenericParameterAdapter() {
								@Override
								public int getIntValue(String key) {
									int limit = rls[0].getTagDownloadLimit();
									if (numTags > 1) {
										for (int i = 1; i < rls.length; i++) {
											int nextLimit = rls[i].getTagDownloadLimit();
											if (nextLimit != limit) {
												return 0;
											}
										}
									}
									return limit < 0 ? limit : limit / DisplayFormatters.getKinB();
								}

								@Override
								public int getIntValue(String key, int def) {
									return getIntValue(key);
								}

								@Override
								public void setIntValue(String key, int value) {
									for (TagFeatureRateLimit rl : rls) {
  									if (value == -1) {
  										rl.setTagDownloadLimit(-1);
  									} else {
  										rl.setTagDownloadLimit(value * DisplayFormatters.getKinB());
  									}
									}
								}

								@Override
								public boolean resetIntDefault(String key) {
									return false;
								}
							}, gTransfer, null, -1, Integer.MAX_VALUE);
					params.maxDownloadSpeed.setLayoutData(gd);
					params.maxDownloadSpeed.setZeroHidden(numTags > 1);

					cols_used += 2;
				}

				// Upload Limit
				if (supportsTagUploadLimit) {
					gd = new GridData();
					label = new Label(gTransfer, SWT.NULL);
					Utils.setLayoutData(label, gd);
					label.setText(k_unit + " " + MessageText.getString(
							"GeneralView.label.maxuploadspeed.tooltip"));

					gd = new GridData();
					//gd.horizontalSpan = 3;
					params.maxUploadSpeed = new GenericIntParameter(
							new GenericParameterAdapter() {
								@Override
								public int getIntValue(String key) {
									int limit = rls[0].getTagUploadLimit();
									if (numTags > 1) {
										for (int i = 1; i < rls.length; i++) {
											int nextLimit = rls[i].getTagUploadLimit();
											if (nextLimit != limit) {
												return 0;
											}
										}
									}
									return limit < 0 ? limit : limit / DisplayFormatters.getKinB();
								}

								@Override
								public int getIntValue(String key, int def) {
									return getIntValue(key);
								}

								@Override
								public void setIntValue(String key, int value) {
									for (TagFeatureRateLimit rl : rls) {
									if (value == -1) {
										rl.setTagUploadLimit(value);
									} else {
										rl.setTagUploadLimit(value * DisplayFormatters.getKinB());
									}}
								}

								@Override
								public boolean resetIntDefault(String key) {
									return false;
								}
							}, gTransfer, null, -1, Integer.MAX_VALUE);
					params.maxUploadSpeed.setLayoutData(gd);
					params.maxUploadSpeed.setZeroHidden(numTags > 1);

					cols_used += 2;
				}

				// Field: Upload Priority
				if (hasTagUploadPriority) {
					params.uploadPriority = new GenericBooleanParameter(
							new BooleanParameterAdapter() {
								@Override
								public Boolean getBooleanValue(String key) {
									int value = -1;
									for (TagFeatureRateLimit rl : rls) {
										value = updateIntBoolean(rl.getTagUploadPriority() > 0, value);
									}
									return value == 2 ? null : value == 1;
								}

								@Override
								public void setBooleanValue(String key, boolean value) {
									for (TagFeatureRateLimit rl : rls) {
										rl.setTagUploadPriority(value ? 1 : 0);
									}
								}
							}, gTransfer, null, "cat.upload.priority");
					gd = new GridData();
					gd.horizontalSpan = 6 - cols_used;
					params.uploadPriority.setLayoutData(gd);
				}

				// Field: Min Share
				if (numTags == 1 && rls[0].getTagMinShareRatio() >= 0) {
					label = new Label(gTransfer, SWT.NONE);
					Messages.setLanguageText(label, "TableColumn.header.min_sr");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);

					params.min_sr = new GenericFloatParameter(
							new GenericParameterAdapter() {
								@Override
								public float getFloatValue(String key) {
									return rls[0].getTagMinShareRatio() / 1000f;
								}

								@Override
								public void setFloatValue(String key, float value) {
									rls[0].setTagMinShareRatio((int) (value * 1000));
								}
							}, gTransfer, null, 0, Float.MAX_VALUE, true, 3);
					gd = new GridData();
					//gd.horizontalSpan = 3;
					gd.widthHint = 75;
					params.min_sr.setLayoutData(gd);
				}

				// Field: Max Share
				if (numTags == 1 && rls[0].getTagMaxShareRatio() >= 0) {
					label = new Label(gTransfer, SWT.NONE);
					Messages.setLanguageText(label, "TableColumn.header.max_sr");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);

					params.max_sr = new GenericFloatParameter(
							new GenericParameterAdapter() {
								@Override
								public float getFloatValue(String key) {
									return rls[0].getTagMaxShareRatio() / 1000f;
								}

								@Override
								public void setFloatValue(String key, float value) {
									rls[0].setTagMaxShareRatio((int) (value * 1000));

									updateTagSRParams( params );
								}
							}, gTransfer, null, 0, Float.MAX_VALUE, true, 3);
					gd = new GridData();
					//gd.horizontalSpan = 3;
					gd.widthHint = 75;
					params.max_sr.setLayoutData(gd);

						// max sr action

					String[] ST_ACTION_VALUES = {
							"" + TagFeatureRateLimit.SR_ACTION_QUEUE,
							"" + TagFeatureRateLimit.SR_ACTION_PAUSE,
							"" + TagFeatureRateLimit.SR_ACTION_STOP,
					};

					String[] ST_ACTION_LABELS = {
							MessageText.getString( "ConfigView.section.queue" ),
							MessageText.getString( "v3.MainWindow.button.pause" ),
							MessageText.getString( "v3.MainWindow.button.stop" ),
					};

					label = new Label(gTransfer, SWT.NONE);
					Messages.setLanguageText(label, "label.when.exceeded");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);
					params.max_sr_action = new GenericStringListParameter(
							new GenericParameterAdapter() {
								@Override
								public String getStringListValue(String key, String def ) {
									return( getStringListValue( key ));
								}
								@Override
								public String
								getStringListValue(
									String		key )
								{
									return( "" + rls[0].getTagMaxShareRatioAction());
								}

								@Override
								public void setStringListValue(String key, String value) {
									rls[0].setTagMaxShareRatioAction( Integer.parseInt( value ));
								}
							},
							gTransfer, "max_sr_action", ""+TagFeatureRateLimit.SR_INDIVIDUAL_ACTION_DEFAULT,
							ST_ACTION_LABELS, ST_ACTION_VALUES );
				}

				// Field: Max Aggregate Share
				if (numTags == 1 && rls[0].getTagAggregateShareRatio() >= 0) {
					label = new Label(gTransfer, SWT.NONE);
					Messages.setLanguageText(label, "TableColumn.header.max_aggregate_sr");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);

					params.max_aggregate_sr = new GenericFloatParameter(
							new GenericParameterAdapter() {
								@Override
								public float getFloatValue(String key) {
									return rls[0].getTagMaxAggregateShareRatio() / 1000f;
								}

								@Override
								public void setFloatValue(String key, float value) {
									rls[0].setTagMaxAggregateShareRatio((int) (value * 1000));

									updateTagSRParams( params );
								}
							}, gTransfer, null, 0, Float.MAX_VALUE, true, 3);
					gd = new GridData();
					//gd.horizontalSpan = 3;
					gd.widthHint = 75;
					params.max_aggregate_sr.setLayoutData(gd);

						// max sr action

					String[] ST_ACTION_VALUES = {
							"" + TagFeatureRateLimit.SR_ACTION_PAUSE,
							"" + TagFeatureRateLimit.SR_ACTION_STOP,
					};

					String[] ST_ACTION_LABELS = {
							MessageText.getString( "v3.MainWindow.button.pause" ),
							MessageText.getString( "v3.MainWindow.button.stop" ),
					};

					label = new Label(gTransfer, SWT.NONE);
					Messages.setLanguageText(label, "label.when.exceeded");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);
					params.max_aggregate_sr_action = new GenericStringListParameter(
							new GenericParameterAdapter() {
								@Override
								public String getStringListValue(String key, String def ) {
									return( getStringListValue( key ));
								}
								@Override
								public String
								getStringListValue(
									String		key )
								{
									return( "" + rls[0].getTagMaxAggregateShareRatioAction());
								}

								@Override
								public void setStringListValue(String key, String value) {
									rls[0].setTagMaxAggregateShareRatioAction( Integer.parseInt( value ));
								}
							},
							gTransfer, "max_aggregate_sr_action", ""+TagFeatureRateLimit.SR_AGGREGATE_ACTION_DEFAULT,
							ST_ACTION_LABELS, ST_ACTION_VALUES );

						// aggregate has priority

					label = new Label(gTransfer, SWT.NONE);
					Messages.setLanguageText(label, "label.aggregate.has.priority");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);
					params.max_aggregate_sr_priority = new GenericBooleanParameter(
							new BooleanParameterAdapter() {
								@Override
								public Boolean
								getBooleanValue(String key)
								{
									return( rls[0].getTagMaxAggregateShareRatioHasPriority());
								}

								@Override
								public void
								setBooleanValue(String key, boolean value)
								{
									 rls[0].setTagMaxAggregateShareRatioHasPriority( value );
								}
							},
							gTransfer, "max_aggregate_sr_priority", TagFeatureRateLimit.AT_RATELIMIT_MAX_AGGREGATE_SR_PRIORITY_DEFAULT );

					updateTagSRParams( params );
				}
			}
			/////////////////////////////////

			if (numTags == 1 && (tags[0] instanceof TagFeatureFileLocation)) {
				final TagFeatureFileLocation fl = (TagFeatureFileLocation) tags[0];

				if (fl.supportsTagCopyOnComplete() || fl.supportsTagInitialSaveFolder()
						|| fl.supportsTagMoveOnComplete()) {

					Group gFiles = new Group(cMainComposite, SWT.NONE);
					gFiles.setText(MessageText.getString( "label.file.settings"));
					gridLayout = new GridLayout(6, false);
					gFiles.setLayout(gridLayout);

					gd = new GridData(SWT.FILL, SWT.NONE, true, false, 4, 1);
					Utils.setLayoutData(gFiles, gd);

					if ( fl.supportsTagInitialSaveFolder()){

						params.initalSaveFolder =
							new folderOption(gFiles,
								"label.init.save.loc")
							{
								@Override
								public void setFolder(File folder) {
									params.initalSaveData.setEnabled( folder != null );
									params.initalSaveTorrent.setEnabled( folder != null );
									fl.setTagInitialSaveFolder(folder);
								}

								@Override
								public File getFolder() {
									File result = fl.getTagInitialSaveFolder();
									params.initalSaveData.setEnabled( result != null );
									params.initalSaveTorrent.setEnabled( result != null );
									return( result );
								}
							};

						params.initalSaveData = new GenericBooleanParameter(
								new BooleanParameterAdapter() {
									@Override
									public Boolean getBooleanValue(String key) {
										return(( fl.getTagInitialSaveOptions() & TagFeatureFileLocation.FL_DATA ) != 0);
									}

									@Override
									public void setBooleanValue(String key, boolean value) {
										long flags = fl.getTagInitialSaveOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										fl.setTagInitialSaveOptions(flags);
									}
								}, gFiles, null, "label.move.data");

						params.initalSaveTorrent = new GenericBooleanParameter(
								new BooleanParameterAdapter() {
									@Override
									public Boolean getBooleanValue(String key) {
										return(( fl.getTagInitialSaveOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public void setBooleanValue(String key, boolean value) {
										long flags = fl.getTagInitialSaveOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										fl.setTagInitialSaveOptions(flags);
									}
								}, gFiles, null, "label.move.torrent");

					}

					if ( fl.supportsTagMoveOnComplete()){

						params.moveOnCompleteFolder =
							new folderOption(gFiles,
								"label.move.on.comp")
							{
								@Override
								public void setFolder(File folder) {

									params.moveOnCompleteData.setEnabled( folder != null );
									params.moveOnCompleteTorrent.setEnabled( folder != null );
									fl.setTagMoveOnCompleteFolder(folder);
								}

								@Override
								public File getFolder() {
									File result = fl.getTagMoveOnCompleteFolder();

									params.moveOnCompleteData.setEnabled( result != null );
									params.moveOnCompleteTorrent.setEnabled( result != null );

									return( result );
								}
							};

						params.moveOnCompleteData = new GenericBooleanParameter(
								new BooleanParameterAdapter() {
									@Override
									public Boolean getBooleanValue(String key) {
										return(( fl.getTagMoveOnCompleteOptions() & TagFeatureFileLocation.FL_DATA ) != 0);
									}

									@Override
									public void setBooleanValue(String key, boolean value) {
										long flags = fl.getTagMoveOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										fl.setTagMoveOnCompleteOptions(flags);
									}
								}, gFiles, null, "label.move.data");

						params.moveOnCompleteTorrent = new GenericBooleanParameter(
								new BooleanParameterAdapter() {
									@Override
									public Boolean getBooleanValue(String key) {
										return(( fl.getTagMoveOnCompleteOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public void setBooleanValue(String key, boolean value) {
										long flags = fl.getTagMoveOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										fl.setTagMoveOnCompleteOptions(flags);
									}
								}, gFiles, null, "label.move.torrent");
					}

					if ( fl.supportsTagCopyOnComplete()){

						params.copyOnCompleteFolder =
							new folderOption(gFiles,
								"label.copy.on.comp")
							{
								@Override
								public void setFolder(File folder)
								{
									params.copyOnCompleteData.setEnabled( folder != null );
									params.copyOnCompleteTorrent.setEnabled( folder != null );
															fl.setTagCopyOnCompleteFolder(folder);
								}

								@Override
								public File getFolder() {
									File result = fl.getTagCopyOnCompleteFolder();
									params.copyOnCompleteData.setEnabled( result != null );
									params.copyOnCompleteTorrent.setEnabled( result != null );
															return( result );
								}
							};

						params.copyOnCompleteData = new GenericBooleanParameter(
								new BooleanParameterAdapter() {
									@Override
									public Boolean getBooleanValue(String key) {
										return(( fl.getTagCopyOnCompleteOptions() & TagFeatureFileLocation.FL_DATA ) != 0);
									}

									@Override
									public void setBooleanValue(String key, boolean value) {
										long flags = fl.getTagCopyOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										fl.setTagCopyOnCompleteOptions(flags);
									}
								}, gFiles, null, "label.copy.data");

						params.copyOnCompleteTorrent = new GenericBooleanParameter(
								new BooleanParameterAdapter() {
									@Override
									public Boolean getBooleanValue(String key) {
										return(( fl.getTagCopyOnCompleteOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public void setBooleanValue(String key, boolean value) {
										long flags = fl.getTagCopyOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										fl.setTagCopyOnCompleteOptions(flags);
									}
								}, gFiles, null, "label.copy.torrent");
					}
				}
			}

			///////////////////////////////

			if (numTags == 1 && tags[0].getTagType().hasTagTypeFeature(TagFeature.TF_PROPERTIES)
					&& (tags[0] instanceof TagFeatureProperties)) {
				TagFeatureProperties tfp = (TagFeatureProperties) tags[0];

				final TagProperty propConstraint = tfp.getProperty(
						TagFeatureProperties.PR_CONSTRAINT);
				if (propConstraint != null) {
					Group gConstraint = new Group(cMainComposite, SWT.NONE);
					Messages.setLanguageText(gConstraint, "tag.property.constraint");
					gridLayout = new GridLayout(5, false);
					gConstraint.setLayout(gridLayout);

					gd = new GridData(SWT.FILL, SWT.NONE, true, false, 4, 1);
					Utils.setLayoutData(gConstraint, gd);

					params.constraints = new Text(gConstraint,
							SWT.WRAP | SWT.BORDER | SWT.MULTI);
					gd = new GridData(SWT.FILL, SWT.NONE, true, false, 5, 1);
					gd.heightHint = 40;
					Utils.setLayoutData(params.constraints, gd);
					params.constraints.addKeyListener(new KeyListener() {
						@Override
						public void keyReleased(KeyEvent e) {
						}

						@Override
						public void keyPressed(KeyEvent e) {
							params.constraints.setData("skipset", 1);
							if (btnSaveConstraint != null && !btnSaveConstraint.isDisposed()) {
								btnSaveConstraint.setEnabled(true);
								btnResetConstraint.setEnabled(true);
							}
						}
					});

					params.constraintError = new Label(gConstraint, SWT.NULL );
					params.constraintError.setForeground( Colors.colorError);
					gd = new GridData(SWT.FILL, SWT.NONE, true, false, 5, 1);
					Utils.setLayoutData(params.constraintError, gd);
					
					btnSaveConstraint = new Button(gConstraint, SWT.PUSH);
					btnSaveConstraint.setEnabled(false);
					btnSaveConstraint.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							String constraint = params.constraints.getText().trim();

							String[] old_value = propConstraint.getStringList();

							if ( constraint.length() == 0 ){
								propConstraint.setStringList( null );
							}else{
								String old_options = old_value.length>1&&old_value[1]!=null?old_value[1]:"";

								if ( old_options.length() == 0 ){
									old_options = CM_ADD_REMOVE;
								}

								propConstraint.setStringList(new String[] {
										constraint, old_options
								});
							}
							if (btnSaveConstraint != null && !btnSaveConstraint.isDisposed()) {
								btnSaveConstraint.setEnabled(false);
								btnResetConstraint.setEnabled(false);
							}
						}
					});
					Messages.setLanguageText(btnSaveConstraint, "Button.save");

					btnResetConstraint = new Button(gConstraint, SWT.PUSH);
					btnResetConstraint.setEnabled(false);
					btnResetConstraint.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							params.constraints.setData("skipset", null);
							swt_updateFields();
							if (btnSaveConstraint != null && !btnSaveConstraint.isDisposed()) {
								btnSaveConstraint.setEnabled(false);
								btnResetConstraint.setEnabled(false);
							}
						}
					});
					Messages.setLanguageText(btnResetConstraint, "Button.reset");

					Label constraintMode = new Label(gConstraint, SWT.NULL );
					Messages.setLanguageText(constraintMode, "label.scope");

					String[] CM_VALUES = {
							CM_ADD_REMOVE,
							CM_ADD_ONLY,
							CM_REMOVE_ONLY
					};

					String[] CM_LABELS = {
							MessageText.getString( "label.addition.and.removal" ),
							MessageText.getString( "label.addition.only" ),
							MessageText.getString( "label.removal.only" ),
					};

					params.constraintMode = new GenericStringListParameter(
							new GenericParameterAdapter() {
								@Override
								public String getStringListValue(String key, String def ) {
									return( getStringListValue( key ));
								}
								@Override
								public String
								getStringListValue(
									String		key )
								{
									String[] list = propConstraint.getStringList();

									if ( list.length > 1 && list[1] != null ){

										return( list[1]);

									}else{

										return( CM_ADD_REMOVE );
									}
								}

								@Override
								public void setStringListValue(String key, String value) {

									if ( value == null || value.length() == 0 ){

										value = CM_ADD_REMOVE;
									}

									String[] list = propConstraint.getStringList();

									propConstraint.setStringList(new String[]{ list!=null&&list.length>0?list[0]:"", value });
								}
							},
							gConstraint, "tag_constraint_action_mode", CM_ADD_REMOVE,
							CM_LABELS, CM_VALUES );

					Link lblAboutConstraint = new Link(gConstraint, SWT.WRAP);
					Utils.setLayoutData(lblAboutConstraint,
							Utils.getWrappableLabelGridData(1, GridData.GRAB_HORIZONTAL));
					lblAboutConstraint.setText(
							MessageText.getString("tag.constraints.info"));
					lblAboutConstraint.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							if (event.text != null && (event.text.startsWith("http://")
									|| event.text.startsWith("https://"))) {
								Utils.launch(event.text);
							}
						}
					});

				}
			}

			if ( 	numTags == 1 &&
					tags[0].getTagType().hasTagTypeFeature(TagFeature.TF_LIMITS )){

				final TagFeatureLimits tfl = (TagFeatureLimits)tags[0];

				if ( tfl.getMaximumTaggables() >= 0 ){

					/////////////////////////////// limits

					Group gLimits = new Group(cMainComposite, SWT.NONE);
					gLimits.setText(MessageText.getString("label.limit.settings"));
					gridLayout = new GridLayout(6, false);
					gLimits.setLayout(gridLayout);

					gd = new GridData(SWT.FILL, SWT.NONE, false, false, 4, 1);
					gLimits.setLayoutData(gd);


					label = new Label(gLimits, SWT.NONE);
					Messages.setLanguageText(label, "TableColumn.header.max_taggables");
					gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
					Utils.setLayoutData(label, gd);

					params.tfl_max_taggables = new GenericIntParameter(
						new GenericParameterAdapter() {
							@Override
							public int getIntValue(String key) {
								return tfl.getMaximumTaggables();
							}
							@Override
							public int getIntValue(String key, int def) {
								return getIntValue(key);
							}
							@Override
							public void setIntValue(String key, int value) {
								tfl.setMaximumTaggables( value );
							}
						}, gLimits, null, 0, Integer.MAX_VALUE );

						// we really don't want partial values to be set as the consequences may be very
						// unwanted if a removal policy is already set...

					params.tfl_max_taggables.disableTimedSave();

					gd = new GridData();
					//gd.horizontalSpan = 3;
					gd.widthHint = 50;
					params.tfl_max_taggables.setLayoutData(gd);

					label = new Label(gLimits, SWT.NONE);
					Messages.setLanguageText(label, "label.removal.policy");

					params.tfl_removal_policy =
						new GenericStringListParameter(
							new GenericParameterAdapter()
							{
								@Override
								public String
								getStringListValue(
									String		key )
								{
									return( String.valueOf( tfl.getRemovalStrategy()));
								}

								@Override
								public String
								getStringListValue(
									String		key,
									String		def )
								{
									return( getStringListValue( key ));
								}

								@Override
								public void
								setStringListValue(
									String		key,
									String		value )
								{
									tfl.setRemovalStrategy( value==null?TagFeatureLimits.RS_DEFAULT:Integer.parseInt( value ));
								}
							},
							gLimits, null,
							new String[]{
								"",
								MessageText.getString( "MyTorrentsView.menu.archive" ),
								MessageText.getString( "Button.deleteContent.fromLibrary" ),
								MessageText.getString( "Button.deleteContent.fromComputer" ),
								MessageText.getString( "label.move.to.old.tag" ),
							},
							new String[]{
								"0",
								"1",
								"2",
								"3",
								"4"});

					label = new Label(gLimits, SWT.NONE);
					Messages.setLanguageText(label, "label.ordering");

					params.tfl_ordering =
						new GenericStringListParameter(
							new GenericParameterAdapter()
							{
								@Override
								public String
								getStringListValue(
									String		key )
								{
									return( String.valueOf( tfl.getOrdering()));
								}

								@Override
								public String
								getStringListValue(
									String		key,
									String		def )
								{
									return( getStringListValue( key ));
								}

								@Override
								public void
								setStringListValue(
									String		key,
									String		value )
								{
									tfl.setOrdering( value==null?TagFeatureLimits.OP_DEFAULT:Integer.parseInt( value ));
								}
							},
							gLimits, null,
							new String[]{
								MessageText.getString( "label.time.added.to.vuze" ),
								MessageText.getString( "label.time.added.to.tag" ),
							},
							new String[]{
								"0",
								"1",
								});
				}
			}

			if ( 	numTags == 1 &&
					tags[0].getTagType().hasTagTypeFeature(TagFeature.TF_NOTIFICATIONS )){

				final TagFeatureNotifications tfn = (TagFeatureNotifications)tags[0];

					// notifications

				Group gNotifications = new Group(cMainComposite, SWT.NONE);
				gNotifications.setText(MessageText.getString("v3.MainWindow.tab.events"));
				gridLayout = new GridLayout(6, false);
				gNotifications.setLayout(gridLayout);

				gd = new GridData(SWT.FILL, SWT.NONE, false, false, 4, 1);
				gNotifications.setLayoutData(gd);

				label = new Label(gNotifications, SWT.NONE);
				label.setText( MessageText.getString( "tag.notification.post" ) + ":" );

				params.notification_post_add = new GenericBooleanParameter(
						new BooleanParameterAdapter() {
							@Override
							public Boolean getBooleanValue(String key) {
								return(( tfn.getPostingNotifications() & TagFeatureNotifications.NOTIFY_ON_ADD ) != 0);
							}

							@Override
							public void setBooleanValue(String key, boolean value) {
								int flags = tfn.getPostingNotifications();
								if ( value ){
									flags |= TagFeatureNotifications.NOTIFY_ON_ADD;
								}else{
									flags &= ~TagFeatureNotifications.NOTIFY_ON_ADD;
								}
								tfn.setPostingNotifications(flags);
							}
						}, gNotifications, null, "label.on.addition");

				params.notification_post_remove = new GenericBooleanParameter(
						new BooleanParameterAdapter() {
							@Override
							public Boolean getBooleanValue(String key) {
								return(( tfn.getPostingNotifications() & TagFeatureNotifications.NOTIFY_ON_REMOVE ) != 0 );
							}

							@Override
							public void setBooleanValue(String key, boolean value) {
								int flags = tfn.getPostingNotifications();
								if ( value ){
									flags |= TagFeatureNotifications.NOTIFY_ON_REMOVE;
								}else{
									flags &= ~TagFeatureNotifications.NOTIFY_ON_REMOVE;
								}
								tfn.setPostingNotifications(flags);
							}
						}, gNotifications, null, "label.on.removal");
			}

			swt_updateFields();
		}
		cMainComposite.layout();
		sc.setMinSize(cMainComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	private void
	updateTagSRParams(
		Params	params )
	{
		boolean	has_individual 	= params.max_sr.getValue() > 0;
		boolean	has_aggregate 	= params.max_aggregate_sr.getValue() > 0;

		params.max_aggregate_sr_priority.getControl().setEnabled( has_individual &&  has_aggregate );
	}

	private int updateIntBoolean(boolean b, int intB) {
		if (intB == -1) {
			intB = b ? 1 : 0;
		} else if ((intB == 1) != b) {
			intB = 2;
		}
		return intB;
	}

	private static abstract class folderOption
	{
		private Button btnClear;

		private Label lblValue;

		public folderOption(final Composite parent, String labelTextID) {
			ImageLoader imageLoader = ImageLoader.getInstance();
			Image imgOpenFolder = imageLoader.getImage("openFolderButton");

			GridData gd = new GridData();
			Label label = new Label(parent, SWT.NONE);
			Utils.setLayoutData(label, gd);
			Messages.setLanguageText(label, labelTextID);

			Button browse = new Button(parent, SWT.PUSH);
			browse.setImage(imgOpenFolder);
			imgOpenFolder.setBackground(browse.getBackground());
			browse.setToolTipText(MessageText.getString("ConfigView.button.browse"));

			browse.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					DirectoryDialog dialog = new DirectoryDialog(parent.getShell(),
							SWT.APPLICATION_MODAL);
					String filterPath;
					File tagInitialSaveFolder = getFolder();
					filterPath = tagInitialSaveFolder != null
							? tagInitialSaveFolder.toString()
							: COConfigurationManager.getStringParameter("Default save path");
					dialog.setFilterPath(filterPath);
					dialog.setMessage(MessageText.getString("label.init.save.loc"));
					dialog.setText(MessageText.getString("label.init.save.loc"));
					final String path = dialog.open();
					if (path != null) {
						Utils.getOffOfSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								setFolder(new File(path));
							}
						});
					}
				}
			});

			lblValue = new Label(parent, SWT.WRAP);
			gd = Utils.getWrappableLabelGridData(1, GridData.FILL_HORIZONTAL);
			gd.verticalAlignment = SWT.CENTER;
			Utils.setLayoutData(lblValue, gd);

			btnClear = new Button(parent, SWT.PUSH);
			Messages.setLanguageText(btnClear, "Button.clear");
			btnClear.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					setFolder(null);
				}
			});
		}

		public void update() {
			File folder = getFolder();
			if (folder == null) {
				Messages.setLanguageText(lblValue, "label.none.assigned");
			} else {
				lblValue.setText(folder.toString());
			}
			btnClear.setVisible(folder != null);
		}

		public abstract File getFolder();

		public abstract void setFolder(File folder);
	}

	private String getFullTitle() {
		return MessageText.getString("TagSettingsView.title");
	}

	private void swt_updateFields() {
		if (tags == null || params == null) {
			initialize(null);
			return;
		}

		int[] tagColor = tags[0].getColor();
		Set<String> listTagNames = new HashSet<>();
		for (Tag tag : tags) {
			TagType tt = tag.getTagType();
			String s = tag.getTagName(true);
			listTagNames.add(s);

			if (tagColor != null) {
				int[] color = tag.getColor();
				if (!Arrays.areEqual(tagColor, color)) {
					tagColor = null;
				}
			}
		}
		String name = GeneralUtils.stringJoin(listTagNames, ", ");

		if (params.cName != null && !params.cName.isDisposed()) {
			if (params.cName instanceof Text) {
				Text txt = (Text) params.cName;
				if (!txt.getText().equals(name)) {
					txt.setText(name);
				}
			} else if (params.cName instanceof Label) {
				Label lbl = (Label) params.cName;
				lbl.setText(name);
			}
		}

		if (params.tagColor != null && tagColor != null) {
			params.tagColor.setColor(tagColor[0], tagColor[1], tagColor[2]);
		}

		if (params.viewInSideBar != null) {
			params.viewInSideBar.refresh();
		}

		if (params.isPublic != null) {
			params.isPublic.refresh();
		}

		if (params.maxDownloadSpeed != null) {
			params.maxDownloadSpeed.resetToDefault();
		}
		if (params.maxUploadSpeed != null) {
			params.maxUploadSpeed.resetToDefault();
		}

		if (params.uploadPriority != null) {
			params.uploadPriority.refresh();
		}
		if (params.min_sr != null) {
			params.min_sr.refresh();
		}
		if (params.max_sr != null) {
			params.max_sr.refresh();
		}

		if (params.initalSaveFolder != null) {
			params.initalSaveFolder.update();
		}
		if (params.moveOnCompleteFolder != null) {
			params.moveOnCompleteFolder.update();
		}
		if (params.copyOnCompleteFolder != null) {
			params.copyOnCompleteFolder.update();
		}
		if (params.constraints != null ) {
			Tag tag = tags[0];

			if ( params.constraints.getData("skipset") == null) {
				String text = "";
				String mode = CM_ADD_REMOVE;
	
				if (tag.getTagType().hasTagTypeFeature(TagFeature.TF_PROPERTIES)
						&& (tag instanceof TagFeatureProperties)) {
					TagFeatureProperties tfp = (TagFeatureProperties) tag;
	
					TagProperty propConstraint = tfp.getProperty(
							TagFeatureProperties.PR_CONSTRAINT);
					if (propConstraint != null) {
						String[] stringList = propConstraint.getStringList();
						// constraint only has one entry
						if ( stringList.length > 0) {
							text = stringList[0];
						}
						if ( stringList.length > 1 && stringList[1] != null ){
							mode = stringList[1];
						}
					}
				}
				params.constraints.setText(text);
				params.constraintMode.setValue( mode );
			}
			String error = (String)tag.getTransientProperty( Tag.TP_CONSTRAINT_ERROR );
			if ( error == null ) {
				params.constraintError.setText( "" );
			}else {
				params.constraintError.setText( error.replace('\r', ' ' ).replace( '\n', ' '));
			}
		}

		if (params.tfl_max_taggables != null) {
			params.tfl_max_taggables.refresh();
		}
	}

	// @see com.biglybt.core.tag.TagTypeListener#tagTypeChanged(com.biglybt.core.tag.TagType)
	@Override
	public void tagTypeChanged(TagType tag_type) {
	}

	@Override
	public void tagEventOccurred(TagEvent event ) {
		int	type = event.getEventType();
		Tag	tag = event.getTag();
		if ( type == TagEvent.ET_TAG_CHANGED ){
			tagChanged( tag );
		}
	}

	public void tagChanged(final Tag changedTag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				if (tags == null) {
					return;
				}
				for (Tag tag : tags) {
					if (changedTag.equals(tag)) {
						swt_updateFields();
						break;
					}
				}
			}
		});
	}


	private static abstract class BooleanParameterAdapter extends GenericParameterAdapter {
		@Override
		public abstract Boolean getBooleanValue(String key);

		@Override
		public Boolean getBooleanValue(String key, Boolean def) {
			return getBooleanValue(key);
		}

		@Override
		public abstract void setBooleanValue(String key, boolean value);
	}
}
