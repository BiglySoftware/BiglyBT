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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListener;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.utils.FontUtils;

/**
 * @author TuxPaper
 * @created Mar 11, 2015
 *
 */
public class TagSettingsView
	implements UISWTViewCoreEventListener, TagTypeListener
{
	public static final String VIEW_ID = "TagSettingsView";
	
	private static final String CM_ADD_REMOVE 	= "am=0;";
	private static final String CM_ADD_ONLY	 	= "am=1;";
	private static final String CM_REMOVE_ONLY	= "am=2;";
	private static final String CM_NEW_DLS		= "am=3;";

	private UISWTView swtView;

	private Composite parent;

	private Composite cMainComposite;

	//private ScrolledComposite sc;

	private Tag[] tags;

	public static class Params
	{
		private Control cName;

		private ColorSwtParameter tagColor;

		private IntSwtParameter maxDownloadSpeed;

		private IntSwtParameter maxUploadSpeed;

		private IconSwtParameter tagIcon;

		private BooleanSwtParameter viewInSideBar;

		private BooleanSwtParameter isPublic;
		
		private BooleanSwtParameter isFilter;

		public BooleanSwtParameter uploadPriority;
		public BooleanSwtParameter boost;

		public IntSwtParameter maxActiveDownloads;
		public IntSwtParameter maxActiveSeeders;
		public BooleanSwtParameter activeLimitsStrict;

		public BooleanSwtParameter firstPrioritySeeding;

		public FloatSwtParameter min_sr;

		public FloatSwtParameter max_sr;

		public StringListSwtParameter	max_sr_action;

		public FloatSwtParameter max_aggregate_sr;

		public StringListSwtParameter	max_aggregate_sr_action;

		public BooleanSwtParameter	max_aggregate_sr_priority;

		private BooleanSwtParameter preventDeletion;

		public folderOption 		initalSaveFolder;
		public BooleanSwtParameter	initalSaveData;
		public BooleanSwtParameter	initalSaveTorrent;

		public folderOption 		moveOnCompleteFolder;
		public BooleanSwtParameter	moveOnCompleteData;
		public BooleanSwtParameter	moveOnCompleteTorrent;

		public folderOption 			copyOnCompleteFolder;
		public BooleanSwtParameter		copyOnCompleteData;
		public BooleanSwtParameter		copyOnCompleteTorrent;

		public folderOption 			moveOnRemoveFolder;
		public BooleanSwtParameter		moveOnRemoveData;
		public BooleanSwtParameter		moveOnRemoveTorrent;

		public folderOption 		moveOnAssignFolder;
		public BooleanSwtParameter	moveOnAssignData;
		public BooleanSwtParameter	moveOnAssignTorrent;


		
		public Text		 				constraints;
		public Label					constraintError;
		public BooleanSwtParameter 		constraintEnabled;
		public StringListSwtParameter 	constraintMode;
		public IntSwtParameter			constraintWeight;
		public IntSwtParameter 			constraintTagSortAuto;

		public IntSwtParameter tfl_max_taggables;

		public StringListSwtParameter	tfl_removal_policy;

		public StringListSwtParameter	tfl_ordering;

		public BooleanSwtParameter	notification_post_add;

		public BooleanSwtParameter	notification_post_remove;
	}

	private Params params = null;

	private Button btnSaveConstraint;

	private Button btnResetConstraint;

	public TagSettingsView() {
	}

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
				parent = (Composite) event.getData();
				buildUI();
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(cMainComposite);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				Object ds = event.getData();
				dataSourceChanged(ds);
				break;

			case UISWTViewEvent.TYPE_SHOWN:
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
		buildUI();
	}

	private void
	addPadding(
		Composite	comp )
	{
		Composite cPad = new Composite(cMainComposite, SWT.NONE);
		GridLayout gridLayout;
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
		gridLayout.marginHeight = gridLayout.marginWidth = 0;
		cPad.setLayout(gridLayout);
		cPad.setLayoutData(new GridData( GridData.FILL_HORIZONTAL));
	}
	
	private void buildUI() {
		if (Utils.runIfNotSWTThread(this::buildUI)) {
			return;
		}

		if (parent == null || parent.isDisposed()) {
			return;
		}
			
		Utils.disposeComposite(parent, false);

		cMainComposite = Utils.createScrolledComposite( parent, SWT.V_SCROLL | SWT.H_SCROLL );

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
			String tagIconFile = tags[0].getImageFile();
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
	  				if (!Arrays.equals(tagColor, color)) {
	  					tagColor = null;
	  				}
				}
				if ( tagIconFile != null ){
					String next = tag.getImageFile();
					if ( next == null || !next.equals( tagIconFile )){
						tagIconFile = null;
					}
				}
			}
			String tagTypes = GeneralUtils.stringJoin(listTagTypes, ", ");

			params = new Params();

			GridData gd;
			GridLayout gridLayout;
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
			gridLayout.marginHeight = gridLayout.marginWidth = 0;
			cMainComposite.setLayout(gridLayout);

			Composite cSection1 = new Composite(cMainComposite, SWT.NONE);
			gridLayout = new GridLayout(6, false);
			gridLayout.marginHeight = 0;
			cSection1.setLayout(gridLayout);
			gd = new GridData(SWT.FILL, SWT.FILL, false, false);
			cSection1.setLayoutData(gd);

			addPadding(cMainComposite);
			
			Composite cSection2 = new Composite(cMainComposite, SWT.NONE);
			gridLayout = new GridLayout(4, false);
			cSection2.setLayout(gridLayout);
			gd = new GridData(SWT.FILL, SWT.FILL, false, false);
			cSection2.setLayoutData(gd);

			addPadding(cMainComposite);
			
			Label label;

			// Field: Tag Type
			label = new Label(cSection1, SWT.NONE);
			FontUtils.setFontHeight(label, 12, SWT.BOLD);
			gd = new GridData();
			gd.horizontalSpan = 6;
			label.setLayoutData(gd);
			label.setText(tagTypes);

			// Field: Name
			label = new Label(cSection1, SWT.NONE);
			Messages.setLanguageText(label, "MinimizedWindow.name");
			gd = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
			label.setLayoutData(gd);

			if (numTags == 1 && !tags[0].getTagType().isTagTypeAuto()) {
				Text txtName = new Text(cSection1, SWT.BORDER);
				params.cName = txtName;
				gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
				gd.widthHint=200;
				txtName.setLayoutData(gd);

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
				gd.widthHint=200;
				label.setLayoutData(gd);
				params.cName = label;
			}

			// Field: Color
			if (tagColor == null) {
				tagColor = new int[] { 0, 0, 0 };
			}

			int[] origTagColor = tagColor == null ? new int[] { 0, 0, 0 } : tagColor;
			
				// trick is that when a tag's explicit colour is that of the tag-type-default then
				// we set the actual tag's colour to null. This is because there is a distinction in how
				// the sidebar renders indicator values when a tag has no explicit colour (as opposed to an
				// explicit colour that happens to be the same as the tag type default)
			
			int[] defaultColor = tags[0].getTagType().getColorDefault();
			
			params.tagColor = new ColorSwtParameter(cSection1, "tagColor",
					"label.color", null, false,
					new SwtParameterValueProcessor<ColorSwtParameter, int[]>() {
						int[] curColor = origTagColor;

						@Override
						public int[] getValue(ColorSwtParameter p) {
							return curColor;
						}

						@Override
						public boolean setValue(ColorSwtParameter p, int[] value) {
							curColor = value;
							
							if ( Arrays.equals( value,  defaultColor )){
								value = null;
							}
							
							for (Tag tag : tags) {
								tag.setColor(value);
							}
							return true;
						}
						
						@Override
						public boolean isDefaultValue(ColorSwtParameter p) {
							
							return( Arrays.equals( getValue(p), defaultColor ));
						}

						@Override
						public boolean resetToDefault(ColorSwtParameter p) {
							setValue(p, defaultColor);
							return true;
						}
					});

			String finalTagIconFile = tagIconFile;
			params.tagIcon = new IconSwtParameter(cSection1, "tagIcon",
					"TableColumn.header.Thumbnail",
					new SwtParameterValueProcessor<IconSwtParameter, String>() {
						String curIconFile = finalTagIconFile;

						@Override
						public String getValue(IconSwtParameter p) {
							return curIconFile;
						}

						@Override
						public boolean setValue(IconSwtParameter p, String value) {
							curIconFile = value;
							for (Tag tag : tags) {

								tag.setImageFile(value);
							}
							return true;
						}

						@Override
						public boolean isDefaultValue(IconSwtParameter p) {
							return curIconFile == null;
						}

						@Override
						public boolean resetToDefault(IconSwtParameter p) {
							setValue(p, null);
							return true;
						}
					});

			boolean allManual 	= true;
			boolean hasCat		= false;
			boolean hasSwarm	= false;
			
			for ( Tag t: tags ){
				
				int type = t.getTagType().getTagType() ;
				
				if ( type != TagType.TT_DOWNLOAD_MANUAL ){
					
					allManual = false;
				}
				
				if ( type == TagType.TT_DOWNLOAD_CATEGORY ){
					
					hasCat = true;
				}
				
				if ( type == TagType.TT_SWARM_TAG ){
					
					hasSwarm = true;
				}
			}

				// for categories the show-in-library/tab and library button visibility isn't controlled
				// by the visibility (or is-filter) settings

			boolean 	hasVisibility 	= !hasCat;
			
			if ( hasVisibility && !hasSwarm ){

				
				// Field: Visible
	
				params.viewInSideBar = new BooleanSwtParameter(cSection2, "viewInSidebar",
						"TagSettings.viewInSideBar", null,
						new BooleanSwtParameter.ValueProcessor() {
							@Override
							public Boolean getValue(BooleanSwtParameter p) {
								int isTagVisible = -1;
								for (Tag tag : tags) {
									isTagVisible = updateIntBoolean(tag.isVisible(), isTagVisible);
								}
								return isTagVisible == 2 ? null : (isTagVisible == 1);
							}
	
							@Override
							public boolean setValue(BooleanSwtParameter p, Boolean value) {
								boolean changed = tags.length == 0;
								for (Tag tag : tags) {
									if (!tag.isVisible() == value) {
										tag.setVisible(value);
										changed = true;
									}
								}
								return changed;
							}
						});
				gd = new GridData();
				gd.horizontalSpan = allManual?1:4;
				params.viewInSideBar.setLayoutData(gd);	
			}
			
			if ( allManual ){
				
				// Field: is filter
			
				
				params.isFilter = new BooleanSwtParameter(cSection2, "isFilter",
						"TagSettings.isFilter", null,
						new BooleanSwtParameter.ValueProcessor() {
							@Override
							public Boolean getValue(BooleanSwtParameter p) {
								int isFilter = -1;
								for (Tag tag : tags) {
									isFilter = updateIntBoolean(tag.getFlag( Tag.FL_IS_FILTER ), isFilter);
								}
								return isFilter == 2 ? null : (isFilter == 1);
							}
	
							@Override
							public boolean setValue(BooleanSwtParameter p, Boolean value) {
								boolean changed = tags.length == 0;
								for (Tag tag : tags) {
									if (!tag.getFlag( Tag.FL_IS_FILTER  ) == value) {
										tag.setFlag(Tag.FL_IS_FILTER , value);
										
										if (value){
											
											if ( !COConfigurationManager.getBooleanParameter( "Library.ShowTagButtons.FiltersOnly" )){
											
												COConfigurationManager.setParameter( "Library.ShowTagButtons", true );
												COConfigurationManager.setParameter( "Library.ShowTagButtons.FiltersOnly", true );
												
												MessageBoxShell mb = new MessageBoxShell(SWT.ICON_INFORMATION| SWT.OK,
														MessageText.getString( "tag.settings.filter.enabled.title"),
														MessageText.getString( "tag.settings.filter.enabled.msg" ));
																
												
												mb.setParent(parent.getShell());
												mb.open(null);
											}
										}
										changed = true;
									}
								}
								return changed;
							}
						});
				gd = new GridData();
				gd.horizontalSpan = 3;
				params.isFilter.setLayoutData(gd);
			}
			
			
			// Field: Public
			if (canBePublic == 1) {
				params.isPublic = new BooleanSwtParameter(cSection2, "tag.isPublic",
						"TagAddWindow.public.checkbox", null,
						new BooleanSwtParameter.ValueProcessor() {
							@Override
							public Boolean getValue(BooleanSwtParameter p) {
								int val = -1;
								for (Tag tag : tags) {
									val = updateIntBoolean(tag.isPublic(), val);
								}
								return val == 2 ? null : (val == 1);
							}

							@Override
							public boolean setValue(BooleanSwtParameter p, Boolean value) {
								boolean changed = tags.length == 0;
								for (Tag tag : tags) {
									if (tag.isPublic() != value) {
										tag.setPublic(value);
										changed = true;
									}
								}
								return changed;
							}
						});
				gd = new GridData();
				gd.horizontalSpan = 4;
				params.isPublic.setLayoutData(gd);
			}

			////////////////////

			if (tagsAreTagFeatureRateLimit) {
				
				Group gTransfer = Utils.createSkinnedGroup(cMainComposite, SWT.NONE);
				gTransfer.setText( MessageText.getString("label.transfer.settings"));
				final int gTransferCols = 8;
				gridLayout = new GridLayout(gTransferCols, false);
				gTransfer.setLayout(gridLayout);
				
				gd = new GridData(SWT.FILL, SWT.NONE, false, false, 1, 1);
				gTransfer.setLayoutData(gd);

				addPadding(cMainComposite);

				final TagFeatureRateLimit rls[] = new TagFeatureRateLimit[tags.length];
				System.arraycopy(tags, 0, rls, 0, tags.length);

				boolean supportsTagDownloadLimit = true;
				boolean supportsTagUploadLimit = true;
				boolean hasTagUploadPriority = true;
				boolean supportsFPSeeding = true;
				boolean supportsMaxDLS = true;
				boolean supportsMaxCDS = true;
				boolean supportsBoost = true;
				for (TagFeatureRateLimit rl : rls) {
					supportsTagDownloadLimit &= rl.supportsTagDownloadLimit();
					supportsTagUploadLimit &= rl.supportsTagUploadLimit();
					hasTagUploadPriority &= rl.getTagUploadPriority() >= 0;
					
					int tt = rl.getTag().getTagType().getTagType();
							
					if ( tt != TagType.TT_DOWNLOAD_MANUAL ){
					
						supportsFPSeeding 	= false;
						supportsMaxDLS		= false;
						supportsMaxCDS		= false;
					}
					if ( tt != TagType.TT_PEER_IPSET ){
						
						supportsBoost = false;
					}
				}

				String k_unit = DisplayFormatters.getRateUnitBase10(
						DisplayFormatters.UNIT_KB).trim();

				int	cols_used = 0;

				// Field: Download Limit
				if (supportsTagDownloadLimit) {

					params.maxDownloadSpeed = new IntSwtParameter(gTransfer,
							"tag.maxDownloadSpeed", "", null, -1, Integer.MAX_VALUE,
							new IntSwtParameter.ValueProcessor() {
								@Override
								public Integer getValue(IntSwtParameter p) {
									java.util.List<Integer> values = new ArrayList<>();
									
									for (int i = 0; i < rls.length; i++) {
										int limit = rls[i].getTagDownloadLimit();
										
										values.add( limit< 0?limit:limit/ DisplayFormatters.getKinB());
									}
									
									return( getValue( values ));
								}

								@Override
								public boolean setValue(IntSwtParameter p, Integer value) {
									if (value == null) {
										return false;
									}
									for (TagFeatureRateLimit rl : rls) {
										if (value == -1) {
											rl.setTagDownloadLimit(-1);
										} else {
											rl.setTagDownloadLimit(
													value * DisplayFormatters.getKinB());
										}
									}
									return true;
								}

								@Override
								public boolean resetToDefault(IntSwtParameter p) {
									return false;
								}
							});
					params.maxDownloadSpeed.setLabelText(k_unit + " " + MessageText.getString(
							"GeneralView.label.maxdownloadspeed.tooltip"));

					cols_used += 2;
				}

				// Upload Limit
				if (supportsTagUploadLimit) {

					params.maxUploadSpeed = new IntSwtParameter(gTransfer,
							"tag.maxUploadSpeed", "", null, -1, Integer.MAX_VALUE,
							new IntSwtParameter.ValueProcessor() {
								@Override
								public Integer getValue(IntSwtParameter p) {
									java.util.List<Integer> values = new ArrayList<>();
									
									for (int i = 0; i < rls.length; i++) {
										int limit = rls[i].getTagUploadLimit();
										
										values.add( limit< 0?limit:limit/ DisplayFormatters.getKinB());
									}
									
									return( getValue( values ));
								}

								@Override
								public boolean setValue(IntSwtParameter p, Integer value) {
									for (TagFeatureRateLimit rl : rls) {
										if (value == -1) {
											rl.setTagUploadLimit(value);
										} else {
											rl.setTagUploadLimit(value * DisplayFormatters.getKinB());
										}
									}
									return true;
								}

								@Override
								public boolean resetToDefault(IntSwtParameter p) {
									return false;
								}
							});

					params.maxUploadSpeed.setLabelText(k_unit + " " + MessageText.getString(
							"GeneralView.label.maxuploadspeed.tooltip"));

					cols_used += 2;
				}

				// Field: Upload Priority
				if (hasTagUploadPriority) {
					params.uploadPriority = new BooleanSwtParameter(gTransfer,
							"tag.uploadPriority", "cat.upload.priority", null,
							new BooleanSwtParameter.ValueProcessor() {
								@Override
								public Boolean getValue(BooleanSwtParameter p) {
									int value = -1;
									for (TagFeatureRateLimit rl : rls) {
										value = updateIntBoolean(rl.getTagUploadPriority() > 0,
												value);
									}
									return value == 2 ? null : value == 1;
								}

								@Override
								public boolean setValue(BooleanSwtParameter p, Boolean value) {
									boolean changed = rls.length == 0;
									int priority = value ? 1 : 0;
									for (TagFeatureRateLimit rl : rls) {
										if (rl.getTagUploadPriority() != priority) {
											rl.setTagUploadPriority(priority);
											changed = true;
										}
									}
									return changed;
								}
							});
					
					cols_used += 2;
				}
				
				// Field: Boost
				if (supportsBoost) {
					params.boost = new BooleanSwtParameter(gTransfer,
							"tag.boost", "PeersView.menu.boost", null,
							new BooleanSwtParameter.ValueProcessor() {
								@Override
								public Boolean getValue(BooleanSwtParameter p) {
									int value = -1;
									for (TagFeatureRateLimit rl : rls) {
										value = updateIntBoolean(rl.getTagBoost(),
												value);
									}
									return value == 2 ? null : value == 1;
								}

								@Override
								public boolean setValue(BooleanSwtParameter p, Boolean value) {
									boolean changed = rls.length == 0;
									boolean boost = value;
									for (TagFeatureRateLimit rl : rls) {
										if (rl.getTagBoost() != boost) {
											rl.setTagBoost(boost);
											changed = true;
										}
									}
									return changed;
								}
							});
					
					cols_used += 2;
				}

				if ( cols_used > 0 && cols_used < gTransferCols){			
					Label lab = new Label( gTransfer, SWT.NULL );
					gd = new GridData();
					gd.horizontalSpan = gTransferCols - cols_used;
					lab.setLayoutData(gd);						
				}
				
				cols_used = 0;
				
				// Field: Min Share
				if (numTags == 1 && rls[0].getTagMinShareRatio() >= 0) {
					params.min_sr = new FloatSwtParameter(gTransfer, "tag.min_sr", "TableColumn.header.min_sr",
							null, 0, Float.MAX_VALUE, true, 3,
							new FloatSwtParameter.ValueProcessor() {
								@Override
								public Float getValue(FloatSwtParameter p) {
									return rls[0].getTagMinShareRatio() / 1000f;
								}

								@Override
								public boolean setValue(FloatSwtParameter p, Float value) {
									int newValue = (int) (value * 1000);
									if (rls[0].getTagMinShareRatio() == newValue) {
										return false;
									}
									rls[0].setTagMinShareRatio(newValue);
									return true;
								}
							});
					
					cols_used += 2;
				}

				// Field: Max Share
				if (numTags == 1 && rls[0].getTagMaxShareRatio() >= 0) {

					params.max_sr = new FloatSwtParameter(gTransfer, "tag.max_sr",
							"TableColumn.header.max_sr", null, 0, Float.MAX_VALUE, true, 3,
							new FloatSwtParameter.ValueProcessor() {
								@Override
								public Float getValue(FloatSwtParameter p) {
									return rls[0].getTagMaxShareRatio() / 1000f;
								}

								@Override
								public boolean setValue(FloatSwtParameter p, Float value) {
									int newValue = (int) (value * 1000);
									if (rls[0].getTagMaxShareRatio() == newValue) {
										return false;
									}
									rls[0].setTagMaxShareRatio(newValue);

									updateTagSRParams(params);
									return true;
								}
							});

						// max sr action

					cols_used += 2;
					
					String[] ST_ACTION_VALUES = {
							"" + TagFeatureRateLimit.SR_ACTION_QUEUE,
							"" + TagFeatureRateLimit.SR_ACTION_PAUSE,
							"" + TagFeatureRateLimit.SR_ACTION_STOP,
							"" + TagFeatureRateLimit.SR_ACTION_ARCHIVE,
							"" + TagFeatureRateLimit.SR_ACTION_REMOVE_FROM_LIBRARY,
							"" + TagFeatureRateLimit.SR_ACTION_REMOVE_FROM_COMPUTER,
					};

					String[] ST_ACTION_LABELS = {
							MessageText.getString( "ConfigView.section.queue" ),
							MessageText.getString( "v3.MainWindow.button.pause" ),
							MessageText.getString( "v3.MainWindow.button.stop" ),
							MessageText.getString( "MyTorrentsView.menu.archive" ),
							MessageText.getString( "Button.deleteContent.fromLibrary" ),
							MessageText.getString( "Button.deleteContent.fromComputer" ),
					};

					params.max_sr_action = new StringListSwtParameter(gTransfer,
							"max_sr_action", "label.when.exceeded", null, ST_ACTION_VALUES, ST_ACTION_LABELS,
							true,
							new StringListSwtParameter.ValueProcessor() {
								@Override
								public String getValue(StringListSwtParameter p) {
									return ("" + rls[0].getTagMaxShareRatioAction());
								}

								@Override
								public boolean setValue(StringListSwtParameter p,
										String value) {
									int val = Integer.parseInt(value);
									if (rls[0].getTagMaxShareRatioAction() != val) {
										rls[0].setTagMaxShareRatioAction(val);
										return true;
									}
									return false;
								}
							});
					
					cols_used += 2;
				}

				if ( cols_used > 0 && cols_used < gTransferCols){			
					Label lab = new Label( gTransfer, SWT.NULL );
					gd = new GridData();
					gd.horizontalSpan = gTransferCols - cols_used;
					lab.setLayoutData(gd);						
				}
				
				cols_used = 0;
				
				// Field: Max Aggregate Share
				if (numTags == 1 && rls[0].getTagAggregateShareRatio() >= 0) {
					params.max_aggregate_sr = new FloatSwtParameter(gTransfer,
							"tag.max_aggregate_sr", "TableColumn.header.max_aggregate_sr", null, 0, Float.MAX_VALUE, true, 3,
							new FloatSwtParameter.ValueProcessor() {
								@Override
								public Float getValue(FloatSwtParameter p) {
									return rls[0].getTagMaxAggregateShareRatio() / 1000f;
								}

								@Override
								public boolean setValue(FloatSwtParameter p, Float value) {
									int newValue = (int) (value * 1000);
									if (rls[0].getTagMaxAggregateShareRatio() == newValue) {
										return false;
									}
									rls[0].setTagMaxAggregateShareRatio(newValue);

									updateTagSRParams(params);
									return true;
								}
							});

					cols_used += 2;
					
						// max sr action

					String[] ST_ACTION_VALUES = {
							"" + TagFeatureRateLimit.SR_ACTION_PAUSE,
							"" + TagFeatureRateLimit.SR_ACTION_STOP,
					};

					String[] ST_ACTION_LABELS = {
							MessageText.getString( "v3.MainWindow.button.pause" ),
							MessageText.getString( "v3.MainWindow.button.stop" ),
					};

					params.max_aggregate_sr_action = new StringListSwtParameter(gTransfer,
							"tag.max_aggregate_sr_action", "label.when.exceeded", null,
							ST_ACTION_VALUES, ST_ACTION_LABELS, true,
							new StringListSwtParameter.ValueProcessor() {
								@Override
								public String getValue(StringListSwtParameter p) {
									return ("" + rls[0].getTagMaxAggregateShareRatioAction());
								}

								@Override
								public boolean setValue(StringListSwtParameter p, String value) {
									int val = Integer.parseInt(value);
									if (rls[0].getTagMaxAggregateShareRatioAction() != val) {
										rls[0].setTagMaxAggregateShareRatioAction(val);
										return true;
									}
									return false;
								}
							});
					
					cols_used += 2;
					
					params.max_aggregate_sr_action.getRelatedControl().setLayoutData(
							new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

						// aggregate has priority

					params.max_aggregate_sr_priority = new BooleanSwtParameter(gTransfer,
							"tag.max_aggregate_sr_priority", "label.aggregate.has.priority",
							null, new BooleanSwtParameter.ValueProcessor() {
								@Override
								public Boolean getValue(BooleanSwtParameter p) {
									return (rls[0].getTagMaxAggregateShareRatioHasPriority());
								}

								@Override
								public boolean setValue(BooleanSwtParameter p, Boolean value) {
									if (rls[0].getTagMaxAggregateShareRatioHasPriority() == value) {
										return false;
									}
									rls[0].setTagMaxAggregateShareRatioHasPriority(value);
									return true;
								}
							});

					cols_used += 2;
					
					updateTagSRParams( params );
				}
				
				if ( cols_used > 0 && cols_used < gTransferCols){			
					Label lab = new Label( gTransfer, SWT.NULL );
					gd = new GridData();
					gd.horizontalSpan = gTransferCols - cols_used;
					lab.setLayoutData(gd);						
				}
				
				cols_used = 0;
				
				if ( numTags == 1 ){
					if (supportsMaxDLS){
						params.maxActiveDownloads = new IntSwtParameter(gTransfer,
								"tag.maxActiveDownloads", "ConfigView.label.maxdownloads.short", null, 0, Integer.MAX_VALUE,
								new IntSwtParameter.ValueProcessor() {
									@Override
									public Integer getValue(IntSwtParameter p) {
										int limit = rls[0].getMaxActiveDownloads();
										if (numTags > 1) {
											for (int i = 1; i < rls.length; i++) {
												int nextLimit = rls[i].getMaxActiveDownloads();
												if (nextLimit != limit) {
													return 0;
												}
											}
										}
										return limit;
									}
	
									@Override
									public boolean setValue(IntSwtParameter p, Integer value) {
										if (value == null) {
											return false;
										}
										for (TagFeatureRateLimit rl : rls) {
											rl.setMaxActiveDownloads(value);
										}
										return true;
									}
								});
	
						cols_used += 2;
					}
					
					if (supportsMaxCDS){
						params.maxActiveSeeders = new IntSwtParameter(gTransfer,
								"tag.maxActiveSeeds", "ConfigView.label.maxseeding", null, 0, Integer.MAX_VALUE,
								new IntSwtParameter.ValueProcessor() {
									@Override
									public Integer getValue(IntSwtParameter p) {
										int limit = rls[0].getMaxActiveSeeds();
										if (numTags > 1) {
											for (int i = 1; i < rls.length; i++) {
												int nextLimit = rls[i].getMaxActiveSeeds();
												if (nextLimit != limit) {
													return 0;
												}
											}
										}
										return limit;
									}
	
									@Override
									public boolean setValue(IntSwtParameter p, Integer value) {
										if (value == null) {
											return false;
										}
										for (TagFeatureRateLimit rl : rls) {
											rl.setMaxActiveSeeds(value);
										}
										return true;
									}
								});
	
						cols_used += 2;
					}
					
					if (supportsMaxDLS||supportsMaxCDS){
						params.activeLimitsStrict = new BooleanSwtParameter(gTransfer,
								"tag.activelimitsstrict", "label.strict.limits", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										int value = -1;
										for (TagFeatureRateLimit rl : rls) {
											value = updateIntBoolean(rl.getStrictActivityLimits(),
													value);
										}
										return value == 2 ? null : value == 1;
									}
	
									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										boolean changed = rls.length == 0;
										for (TagFeatureRateLimit rl : rls) {
											if (rl.getStrictActivityLimits() != value) {
												rl.setStrictActivityLimits(value);
												changed = true;
											}
										}
										return changed;
									}
								});
						cols_used += 2;
					}
					
					if ( cols_used > 0 && cols_used < gTransferCols){			
						Label lab = new Label( gTransfer, SWT.NULL );
						gd = new GridData();
						gd.horizontalSpan = gTransferCols - cols_used;
						lab.setLayoutData(gd);		
						cols_used = 0;
					}
				}
				
				if (supportsFPSeeding) {
					params.firstPrioritySeeding = new BooleanSwtParameter(gTransfer,
							"tag.firstPrioritySeeding", "label.first.priority.seeding", null,
							new BooleanSwtParameter.ValueProcessor() {
								@Override
								public Boolean getValue(BooleanSwtParameter p) {
									int value = -1;
									for (TagFeatureRateLimit rl : rls) {
										value = updateIntBoolean(rl.getFirstPrioritySeeding(),
												value);
									}
									return value == 2 ? null : value == 1;
								}

								@Override
								public boolean setValue(BooleanSwtParameter p, Boolean value) {
									boolean changed = rls.length == 0;
									for (TagFeatureRateLimit rl : rls) {
										if (rl.getFirstPrioritySeeding() != value) {
											rl.setFirstPrioritySeeding(value);
											changed = true;
										}
									}
									return changed;
								}
							});
					cols_used += 2;
				}
				
				if ( cols_used > 0 && cols_used < gTransferCols){			
					Label lab = new Label( gTransfer, SWT.NULL );
					gd = new GridData();
					gd.horizontalSpan = gTransferCols - cols_used;
					lab.setLayoutData(gd);						
				}
			}
			
			/////////////////////////////////

			if (numTags == 1 && (tags[0] instanceof TagFeatureFileLocation)) {
				final TagFeatureFileLocation fl = (TagFeatureFileLocation) tags[0];

				if (	fl.supportsTagCopyOnComplete() || 
						fl.supportsTagInitialSaveFolder() || 
						fl.supportsTagMoveOnComplete() ||
						fl.supportsTagMoveOnRemove() ||
						fl.supportsTagMoveOnAssign()) {

					Group gFiles = Utils.createSkinnedGroup(cMainComposite, SWT.NONE);
					gFiles.setText(MessageText.getString( "label.file.settings"));
					// label, button, value, cb*2, cb*2 = 8
					gridLayout = new GridLayout(8, false);
					gFiles.setLayout(gridLayout);

					gd = new GridData(SWT.FILL, SWT.NONE, false, false, 1, 1);
					gFiles.setLayoutData(gd);

					addPadding(cMainComposite);

					params.preventDeletion = new BooleanSwtParameter(gFiles,
							"tag.prevent_delete", "label.prevent.dl.delete",
							null, new BooleanSwtParameter.ValueProcessor() {
								@Override
								public Boolean getValue(BooleanSwtParameter p) {
									return(fl.getPreventDelete());
								}

								@Override
								public boolean setValue(BooleanSwtParameter p, Boolean value) {
									if (fl.getPreventDelete() == value) {
										return false;
									}
									fl.setPreventDelete(value);
									return true;
								}
							});
					gd = new GridData();
					gd.horizontalSpan = 8;
					params.preventDeletion.setLayoutData(gd);
					
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

						params.initalSaveData = new BooleanSwtParameter(gFiles,
								"tag.initalSaveData", "label.move.data", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return ((fl.getTagInitialSaveOptions()
												& TagFeatureFileLocation.FL_DATA) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p,
											Boolean value) {
										long flags = fl.getTagInitialSaveOptions();
										if (value) {
											flags |= TagFeatureFileLocation.FL_DATA;
										} else {
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										if (fl.getTagInitialSaveOptions() != flags) {
											fl.setTagInitialSaveOptions(flags);
											return true;
										}
										return false;
									}
								});

						params.initalSaveTorrent = new BooleanSwtParameter(gFiles,
								"tag.initalSaveTorrent", "label.move.torrent", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return ((fl.getTagInitialSaveOptions()
												& TagFeatureFileLocation.FL_TORRENT) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p,
											Boolean value) {
										long flags = fl.getTagInitialSaveOptions();
										if (value) {
											flags |= TagFeatureFileLocation.FL_TORRENT;
										} else {
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										if (fl.getTagInitialSaveOptions() != flags) {
											fl.setTagInitialSaveOptions(flags);
											return true;
										}
										return false;
									}
								});

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

						params.moveOnCompleteData = new BooleanSwtParameter(gFiles,
								"tag.moveOnCompleteData", "label.move.data", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return(( fl.getTagMoveOnCompleteOptions() & TagFeatureFileLocation.FL_DATA ) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagMoveOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										if (fl.getTagMoveOnCompleteOptions() != flags) {
											fl.setTagMoveOnCompleteOptions(flags);
											return true;
										}
										return false;
									}
								});

						params.moveOnCompleteTorrent = new BooleanSwtParameter(gFiles,
								"tag.moveOnCompleteTorrent", "label.move.torrent", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return(( fl.getTagMoveOnCompleteOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagMoveOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										if (fl.getTagMoveOnCompleteOptions() != flags) {
											fl.setTagMoveOnCompleteOptions(flags);
											return true;
										}
										return false;
									}
								});
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

						params.copyOnCompleteData = new BooleanSwtParameter(gFiles,
								"tag.copyOnCompleteData", "label.copy.data", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return ((fl.getTagCopyOnCompleteOptions()
												& TagFeatureFileLocation.FL_DATA) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagCopyOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										if (fl.getTagCopyOnCompleteOptions() != flags) {
											fl.setTagCopyOnCompleteOptions(flags);
											return true;
										}
										return false;
									}
								});

						params.copyOnCompleteTorrent = new BooleanSwtParameter(gFiles,
								"tag.copyOnCompleteTorrent", "label.copy.torrent", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return(( fl.getTagCopyOnCompleteOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagCopyOnCompleteOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										if (fl.getTagCopyOnCompleteOptions() != flags) {
											fl.setTagCopyOnCompleteOptions(flags);
											return true;
										}
										return false;
									}
								});
					}
					
					if ( fl.supportsTagMoveOnRemove()){

						params.moveOnRemoveFolder =
							new folderOption(gFiles,
								"label.move.on.rem")
							{
								@Override
								public void setFolder(File folder)
								{
									params.moveOnRemoveData.setEnabled( folder != null );
									params.moveOnRemoveTorrent.setEnabled( folder != null );
															fl.setTagMoveOnRemoveFolder(folder);
								}

								@Override
								public File getFolder() {
									File result = fl.getTagMoveOnRemoveFolder();
									params.moveOnRemoveData.setEnabled( result != null );
									params.moveOnRemoveTorrent.setEnabled( result != null );
															return( result );
								}
							};

						params.moveOnRemoveData = new BooleanSwtParameter(gFiles,
								"tag.moveOnRemoveData", "label.move.data", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return ((fl.getTagMoveOnRemoveOptions()
												& TagFeatureFileLocation.FL_DATA) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagMoveOnRemoveOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										if (fl.getTagMoveOnRemoveOptions() != flags) {
											fl.setTagMoveOnRemoveOptions(flags);
											return true;
										}
										return false;
									}
								});

						params.moveOnRemoveTorrent = new BooleanSwtParameter(gFiles,
								"tag.moveOnRemoveTorrent", "label.move.torrent", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return(( fl.getTagMoveOnRemoveOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagMoveOnRemoveOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										if (fl.getTagMoveOnRemoveOptions() != flags) {
											fl.setTagMoveOnRemoveOptions(flags);
											return true;
										}
										return false;
									}
								});
					}
					
					if ( fl.supportsTagMoveOnAssign()){

						params.moveOnAssignFolder =
							new folderOption(gFiles,
								"label.move.on.assign")
							{
								@Override
								public void setFolder(File folder)
								{
									params.moveOnAssignData.setEnabled( folder != null );
									params.moveOnAssignTorrent.setEnabled( folder != null );
															fl.setTagMoveOnAssignFolder(folder);
								}

								@Override
								public File getFolder() {
									File result = fl.getTagMoveOnAssignFolder();
									params.moveOnAssignData.setEnabled( result != null );
									params.moveOnAssignTorrent.setEnabled( result != null );
															return( result );
								}
							};

						params.moveOnAssignData = new BooleanSwtParameter(gFiles,
								"tag.moveOnAssignData", "label.move.data", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return ((fl.getTagMoveOnAssignOptions()
												& TagFeatureFileLocation.FL_DATA) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagMoveOnAssignOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_DATA;
										}else{
											flags &= ~TagFeatureFileLocation.FL_DATA;
										}
										if (fl.getTagMoveOnAssignOptions() != flags) {
											fl.setTagMoveOnAssignOptions(flags);
											return true;
										}
										return false;
									}
								});

						params.moveOnAssignTorrent = new BooleanSwtParameter(gFiles,
								"tag.moveOnAssignTorrent", "label.move.torrent", null,
								new BooleanSwtParameter.ValueProcessor() {
									@Override
									public Boolean getValue(BooleanSwtParameter p) {
										return(( fl.getTagMoveOnAssignOptions() & TagFeatureFileLocation.FL_TORRENT ) != 0);
									}

									@Override
									public boolean setValue(BooleanSwtParameter p, Boolean value) {
										long flags = fl.getTagMoveOnAssignOptions();
										if ( value ){
											flags |= TagFeatureFileLocation.FL_TORRENT;
										}else{
											flags &= ~TagFeatureFileLocation.FL_TORRENT;
										}
										if (fl.getTagMoveOnAssignOptions() != flags) {
											fl.setTagMoveOnAssignOptions(flags);
											return true;
										}
										return false;
									}
								});
					}
				}
			}

			///////////////////////////////

			if (numTags == 1 && tags[0].getTagType().hasTagTypeFeature(TagFeature.TF_PROPERTIES)
					&& (tags[0] instanceof TagFeatureProperties)) {
				TagFeatureProperties tfp = (TagFeatureProperties) tags[0];

				final TagProperty propConstraint = tfp.getProperty(
						TagFeatureProperties.PR_CONSTRAINT);
				
				TagDownload tag_dl = tags[0] instanceof TagDownload?(TagDownload)tags[0]:null;

				if (propConstraint != null) {
					Group gConstraint = Utils.createSkinnedGroup(cMainComposite, SWT.NONE);
					Messages.setLanguageText(gConstraint, "tag.property.constraint");
					gridLayout = new GridLayout();
					gConstraint.setLayout(gridLayout);

					gd = new GridData(SWT.FILL, SWT.NONE, false, false, 1, 1);
					gConstraint.setLayoutData(gd);

					addPadding(cMainComposite);

					params.constraints = new Text(gConstraint,
							SWT.WRAP | SWT.BORDER | SWT.MULTI);
					gd = new GridData(SWT.FILL, SWT.NONE, true, false, 1, 1);
					gd.heightHint = 40;
					params.constraints.setLayoutData(gd);
					params.constraints.addKeyListener(new KeyListener() {
						@Override
						public void keyReleased(KeyEvent e) {
						}

						@Override
						public void keyPressed(KeyEvent e) {
							int key = e.character;

							if ( key <= 26 && key > 0 ){

								key += 'a' - 1;
							}

							if ( key == 'a' && e.stateMask == SWT.MOD1 ){

								e.doit = false;

								params.constraints.selectAll();
							}
							
							params.constraints.setData("skipset", 1);
							if (btnSaveConstraint != null && !btnSaveConstraint.isDisposed()) {
								btnSaveConstraint.setEnabled(true);
								btnResetConstraint.setEnabled(true);
							}
						}
					});

						// ctrl+tab to exit the text box
					
					params.constraints.addListener( SWT.Traverse, (e)->{
						if ( e.detail == SWT.TRAVERSE_TAB_NEXT && ( e.stateMask & SWT.MOD1 ) != 0 ){
						
							e.doit = true;
						}
					});
					
					params.constraintError = new Label(gConstraint, SWT.NULL );
					params.constraintError.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ));
					
					Utils.setSkinnedForeground( params.constraintError, Colors.colorError, true );

					Composite cConstraintOptions = new Composite(gConstraint, SWT.NULL);
					cConstraintOptions.setLayoutData(new GridData( GridData.FILL_HORIZONTAL));
					gridLayout = new GridLayout(11,false);
					gridLayout.marginLeft = gridLayout.marginRight = gridLayout.marginWidth = 0;
					gridLayout.marginTop = gridLayout.marginBottom = gridLayout.marginHeight = 0;
					cConstraintOptions.setLayout(gridLayout);
					
					btnSaveConstraint = new Button(cConstraintOptions, SWT.PUSH);
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

					btnResetConstraint = new Button(cConstraintOptions, SWT.PUSH);
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
				
					params.constraintEnabled = new BooleanSwtParameter(cConstraintOptions,
							"tag.constraintEnabled", "label.enabled", null,
							new BooleanSwtParameter.ValueProcessor() {
								@Override
								public Boolean getValue(BooleanSwtParameter p) {
									return(propConstraint.isEnabled());
								}

								@Override
								public boolean setValue(BooleanSwtParameter p, Boolean value) {
									if (propConstraint.isEnabled() == value) {
										return false;
									}
									propConstraint.setEnabled( value );
									return true;
								}
							});
					
					String[] CM_VALUES = {
							CM_ADD_REMOVE,
							CM_ADD_ONLY,
							CM_REMOVE_ONLY,
							CM_NEW_DLS,
					};

					String[] CM_LABELS = {
							MessageText.getString( "label.addition.and.removal" ),
							MessageText.getString( "label.addition.only" ),
							MessageText.getString( "label.removal.only" ),
							MessageText.getString( "label.new.downloads" ),
					};

					params.constraintMode = new StringListSwtParameter(cConstraintOptions,
							"tag_constraint_action_mode", "label.scope", null, CM_VALUES,
							CM_LABELS, true, new StringListSwtParameter.ValueProcessor() {
								@Override
								public String getValue(StringListSwtParameter p) {
									String[] list = propConstraint.getStringList();

									if (list.length > 1 && list[1] != null) {

										return (list[1]);

									} else {

										return (CM_ADD_REMOVE);
									}
								}

								@Override
								public boolean setValue(StringListSwtParameter p, String value) {

									if (value == null || value.length() == 0) {

										value = CM_ADD_REMOVE;
									}

									String[] list = propConstraint.getStringList();

									propConstraint.setStringList(new String[]{ list!=null&&list.length>0?list[0]:"", value });

									return true;
								}
							});

					params.constraintWeight = new IntSwtParameter(cConstraintOptions,
							"tag_constraint_weight", "tag.constraints.weight", null,
							Integer.MIN_VALUE, Integer.MAX_VALUE, new IntSwtParameter.ValueProcessor() {
								@Override
								public Integer getValue(IntSwtParameter p) {
									return(((TagDownload)tags[0]).getWeight());
								}

								@Override
								public boolean setValue(IntSwtParameter p, Integer value) {
									((TagDownload)tags[0]).setWeight( value );
									return true;
								}
							} );
					
					params.constraintWeight.setEnabled( tag_dl != null );
					
					Link lblAboutConstraint = new Link(cConstraintOptions, SWT.WRAP);
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

					ClipboardCopy.addCopyToClipMenu(lblAboutConstraint);
					
					Composite cApplySort = new Composite(gConstraint, SWT.NULL);
					cApplySort.setLayoutData(new GridData( GridData.FILL_HORIZONTAL));
					
					gridLayout = new GridLayout(10,false);
					gridLayout.marginLeft = gridLayout.marginRight = gridLayout.marginWidth = 0;
					gridLayout.marginTop = gridLayout.marginBottom = gridLayout.marginHeight = 0;
					cApplySort.setLayout( gridLayout );
										
					Label lApplySort = new Label( cApplySort, SWT.NULL );
					
					lApplySort.setText(	MessageText.getString( "tag.constraints.sort.apply.manual" ));
					
					Button btnApply = new Button(cApplySort, SWT.PUSH);
		
					btnApply.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							tag_dl.applySort();
						}
					});
					Messages.setLanguageText(btnApply, "Button.apply");
					btnApply.setEnabled( tag_dl != null );
					
					params.constraintTagSortAuto = new IntSwtParameter(cApplySort,
							"tag_constraint_tag_sort_auto", "tag.constraints.sort.apply.auto", null,
							0, Integer.MAX_VALUE, new IntSwtParameter.ValueProcessor() {
								@Override
								public Integer getValue(IntSwtParameter p) {
									return(((TagDownload)tags[0]).getAutoApplySortInterval());
								}

								@Override
								public boolean setValue(IntSwtParameter p, Integer value) {
									((TagDownload)tags[0]).setAutoApplySortInterval( value );
									return true;
								}
							} );
					
					params.constraintTagSortAuto.setEnabled( tag_dl != null );
				}
			}

			if ( 	numTags == 1 &&
					tags[0].getTagType().hasTagTypeFeature(TagFeature.TF_LIMITS )){

				final TagFeatureLimits tfl = (TagFeatureLimits)tags[0];

				if ( tfl.getMaximumTaggables() >= 0 ){

					/////////////////////////////// limits

					Group gLimits = Utils.createSkinnedGroup(cMainComposite, SWT.NONE);
					gLimits.setText(MessageText.getString("label.limit.settings"));
					gridLayout = new GridLayout(6, false);
					gLimits.setLayout(gridLayout);

					gd = new GridData(SWT.FILL, SWT.NONE, false, false, 1, 1);
					gLimits.setLayoutData(gd);

					addPadding(cMainComposite);

					params.tfl_max_taggables = new IntSwtParameter(gLimits,
							"tag.tfl_max_taggables", "TableColumn.header.max_taggables", null,
							0, Integer.MAX_VALUE, new IntSwtParameter.ValueProcessor() {
								@Override
								public Integer getValue(IntSwtParameter p) {
									return tfl.getMaximumTaggables();
								}

								@Override
								public boolean setValue(IntSwtParameter p, Integer value) {
									tfl.setMaximumTaggables( value );
									return true;
								}
							} );

						// we really don't want partial values to be set as the consequences may be very
						// unwanted if a removal policy is already set...

					params.tfl_max_taggables.disableTimedSave();

					params.tfl_removal_policy = new StringListSwtParameter(gLimits,
							"tag.tfl_removal_policy", "label.removal.policy", null,
							new String[] {
								"0",
								"1",
								"2",
								"3",
								"4"
							}, new String[] {
								"",
								MessageText.getString("MyTorrentsView.menu.archive"),
								MessageText.getString("Button.deleteContent.fromLibrary"),
								MessageText.getString("Button.deleteContent.fromComputer"),
								MessageText.getString("label.move.to.old.tag"),
							}, true, new StringListSwtParameter.ValueProcessor() {
								@Override
								public String getValue(StringListSwtParameter p) {
									return (String.valueOf(tfl.getRemovalStrategy()));
								}

								@Override
								public boolean setValue(StringListSwtParameter p, String value) {
									int val = value == null
											? TagFeatureLimits.RS_DEFAULT : Integer.parseInt(value);
									if (tfl.getRemovalStrategy() != val) {
										tfl.setRemovalStrategy(val);
										return true;
									}
									return false;
								}
							});

					params.tfl_ordering = new StringListSwtParameter(gLimits,
							"tag.tfl_ordering", "label.ordering", null, new String[] {
								"0",
								"1",
							}, new String[] {
								MessageText.getString("label.time.added.to.vuze"),
								MessageText.getString("label.time.added.to.tag"),
							}, true, new StringListSwtParameter.ValueProcessor() {
								@Override
								public String getValue(StringListSwtParameter p) {
									return (String.valueOf(tfl.getOrdering()));
								}

								@Override
								public boolean setValue(StringListSwtParameter p, String value) {
									int val = value == null ? TagFeatureLimits.OP_DEFAULT
											: Integer.parseInt(value);
									if (tfl.getOrdering() != val) {
										tfl.setOrdering(val);
										return true;
									}
									return false;
								}
							});
				}
			}

			if ( 	numTags == 1 &&
					tags[0].getTagType().hasTagTypeFeature(TagFeature.TF_NOTIFICATIONS )){

				final TagFeatureNotifications tfn = (TagFeatureNotifications)tags[0];

					// notifications

				Group gNotifications = Utils.createSkinnedGroup(cMainComposite, SWT.NONE);
				gNotifications.setText(MessageText.getString("v3.MainWindow.tab.events"));
				gridLayout = new GridLayout(6, false);
				gNotifications.setLayout(gridLayout);

				gd = new GridData(SWT.FILL, SWT.NONE, false, false, 1, 1);
				gNotifications.setLayoutData(gd);
				
				addPadding(cMainComposite);
				label = new Label(gNotifications, SWT.NONE);
				label.setText( MessageText.getString( "tag.notification.post" ) + ":" );

				params.notification_post_add = new BooleanSwtParameter(gNotifications,
						"tag.notification_post_add", "label.on.addition", null,
						new BooleanSwtParameter.ValueProcessor() {
							@Override
							public Boolean getValue(BooleanSwtParameter p) {
								return(( tfn.getPostingNotifications() & TagFeatureNotifications.NOTIFY_ON_ADD ) != 0);
							}

							@Override
							public boolean setValue(BooleanSwtParameter p, Boolean value) {
								int flags = tfn.getPostingNotifications();
								if ( value ){
									flags |= TagFeatureNotifications.NOTIFY_ON_ADD;
								}else{
									flags &= ~TagFeatureNotifications.NOTIFY_ON_ADD;
								}
								if (tfn.getPostingNotifications() != flags) {
									tfn.setPostingNotifications(flags);
									return true;
								}
								return false;
							}
						});

				params.notification_post_remove = new BooleanSwtParameter(
						gNotifications, "tag.notification_post_remove", "label.on.removal",
						null, new BooleanSwtParameter.ValueProcessor() {
							@Override
							public Boolean getValue(BooleanSwtParameter p) {
								return(( tfn.getPostingNotifications() & TagFeatureNotifications.NOTIFY_ON_REMOVE ) != 0 );
							}

							@Override
							public boolean setValue(BooleanSwtParameter p, Boolean value) {
								int flags = tfn.getPostingNotifications();
								if ( value ){
									flags |= TagFeatureNotifications.NOTIFY_ON_REMOVE;
								}else{
									flags &= ~TagFeatureNotifications.NOTIFY_ON_REMOVE;
								}
								if (tfn.getPostingNotifications() != flags) {
									tfn.setPostingNotifications(flags);
									return true;
								}
								return false;
							}
						});
			}

			swt_updateFields();
		}
		parent.layout( true, true );
	}

	private void
	updateTagSRParams(
		Params	params )
	{
		boolean	has_individual 	= params.max_sr.getValue() > 0;
		boolean	has_aggregate 	= params.max_aggregate_sr.getValue() > 0;

		params.max_aggregate_sr_priority.getMainControl().setEnabled( has_individual &&  has_aggregate );
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
			label.setLayoutData(gd);
			Messages.setLanguageText(label, labelTextID);

			Button browse = new Button(parent, SWT.PUSH);
			browse.setImage(imgOpenFolder);
			imgOpenFolder.setBackground(browse.getBackground());
			Utils.setTT(browse,MessageText.getString("ConfigView.button.browse"));

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
			lblValue.setLayoutData(gd);

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
			buildUI();
			return;
		}

		int[] tagColor = tags[0].getColor();
		Set<String> listTagNames = new HashSet<>();
		for (Tag tag : tags) {
			String s = tag.getTagName(true);
			listTagNames.add(s);

			if (tagColor != null) {
				int[] color = tag.getColor();
				if (!Arrays.equals(tagColor, color)) {
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
			params.viewInSideBar.refreshControl();
		}

		if (params.isPublic != null) {
			params.isPublic.refreshControl();
		}

		if (params.isFilter != null) {
			params.isFilter.refreshControl();
		}

		if (params.maxDownloadSpeed != null) {
			params.maxDownloadSpeed.resetToDefault();
		}
		if (params.maxUploadSpeed != null) {
			params.maxUploadSpeed.resetToDefault();
		}

		if (params.uploadPriority != null) {
			params.uploadPriority.refreshControl();
		}
		if (params.min_sr != null) {
			params.min_sr.refreshControl();
		}
		if (params.max_sr != null) {
			params.max_sr.refreshControl();
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
		if (params.moveOnRemoveFolder != null) {
			params.moveOnRemoveFolder.update();
		}
		if (params.moveOnAssignFolder != null) {
			params.moveOnAssignFolder.update();
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
			params.tfl_max_taggables.refreshControl();
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
		if ( type == TagEvent.ET_TAG_MEMBERSHIP_CHANGED || type == TagEvent.ET_TAG_METADATA_CHANGED ){
			tagChanged( tag );
		}
	}

	public void tagChanged(final Tag changedTag) {
		Utils.execSWTThread(() -> {
			if (tags == null) {
				return;
			}
			for (Tag tag : tags) {
				if (changedTag.equals(tag)) {
					swt_updateFields();
					break;
				}
			}
		});
	}
}
