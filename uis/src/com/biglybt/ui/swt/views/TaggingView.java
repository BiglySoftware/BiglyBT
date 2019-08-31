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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.utils.TagButtonsUI;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.ui.swt.widgets.TagCanvas;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;

import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.ui.UIFunctions.TagReturner;

/**
 * View showing tags set on selected taggable item(s).  Sometimes easier than
 * drag and dropping to buttons/sidebar
 *
 * @author TuxPaper
 * @created Mar 23, 2015
 *
 */
public class TaggingView
	implements UISWTViewCoreEventListenerEx, TagTypeListener
{
	public static final String MSGID_PREFIX = "TaggingView";

	private UISWTView swtView;

	private ScrolledComposite sc;

	private List<Taggable> taggables;

	private Composite parent;

	private TagButtonsUI 	tagButtonsUI;
	private Button			buttonExplain;
	private Composite mainComposite;

	public TaggingView() {
	}
	
	@Override
	public boolean
	isCloneable()
	{
		return( true );
	}

	@Override
	public UISWTViewCoreEventListenerEx
	getClone()
	{
		return( new TaggingView());
	}

	@Override
	public CloneConstructor
	getCloneConstructor()
	{
		return( 
			new CloneConstructor()
			{
				public Class<? extends UISWTViewCoreEventListenerEx>
				getCloneClass()
				{
					return( TaggingView.class );
				}
				
				public List<Object>
				getParameters()
				{
					return( null );
				}
			});
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
				parent = (Composite) event.getData();
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(mainComposite);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				Object ds = event.getData();
				dataSourceChanged(ds);
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				initialize();
				if (taggables == null) {
					dataSourceChanged(swtView.getDataSource());
				}
				break;

			case UISWTViewEvent.TYPE_FOCUSLOST:
				delete();
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;
		}

		return true;
	}

	private void delete() {
		Utils.disposeComposite(sc);
		dataSourceChanged(null);
	}

	private void refresh() {
	}

	private void dataSourceChanged(Object ds) {
		boolean wasNull = taggables == null;

		if (ds instanceof Taggable) {
			taggables = new ArrayList<>();
			taggables.add((Taggable) ds);
		} else if (ds instanceof Taggable[]) {
			taggables = new ArrayList<>();
			taggables.addAll(Arrays.asList((Taggable[]) ds));
		} else if (ds instanceof Object[]) {
			taggables = new ArrayList<>();
			Object[] objects = (Object[]) ds;
			for (Object o : objects) {
				if (o instanceof Taggable) {
					Taggable taggable = (Taggable) o;
					if ( !taggables.contains( taggable )){
						taggables.add(taggable);
					}
				} else if (o instanceof DiskManagerFileInfo) {
					DownloadManager temp = ((DiskManagerFileInfo) o).getDownloadManager();
					if (temp != null) {
						if (!taggables.contains(temp)) {
							taggables.add(temp);
						}
					}
				}
			}
			if (taggables.size() == 0) {
				taggables = null;
			}
		} else {
			taggables = null;
		}

		boolean isNull = taggables == null;
		if (isNull != wasNull) {
			TagManager tm = TagManagerFactory.getTagManager();
			TagType tagType;
			/*
			tagType = tm.getTagType(TagType.TT_DOWNLOAD_CATEGORY);
			if (isNull) {
				tagType.removeTagTypeListener(this);
			} else {
				tagType.addTagTypeListener(this, false);
			}
			*/
			tagType = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);
			if (isNull) {
				tagType.removeTagTypeListener(this);
			} else {
				tagType.addTagTypeListener(this, false);
			}
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_updateFields();
			}
		});
	}

	private void initialize() {
		if (mainComposite == null || mainComposite.isDisposed()) {
			if (parent == null || parent.isDisposed()) {
				return;
			}

			mainComposite = new Composite(parent, SWT.NONE);
			mainComposite.setBackground(
					parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));

			GridLayout layout = new GridLayout();
			layout.marginHeight = layout.marginWidth = layout.verticalSpacing = 0;
			mainComposite.setLayout(layout);
			Layout parentLayout = parent.getLayout();
			if (parentLayout instanceof GridLayout) {
				GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
				mainComposite.setLayoutData(gd);
			} else if (parentLayout instanceof FormLayout) {
				mainComposite.setLayoutData(Utils.getFilledFormData());
			}

		} else {
			Utils.disposeComposite(mainComposite, false);
		}

		tagButtonsUI = new TagButtonsUI();

		List<Tag> listAllTags = getTags();

		boolean hasGroup = false;
		for (Tag tag : listAllTags) {
			String group = tag.getGroup();
			if (group != null && group.length() > 0) {
				hasGroup = true;
				break;
			}
		}

		/// Buttons

		Composite buttonComp = new Composite(mainComposite, SWT.NONE);

		GridData layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		buttonComp.setLayoutData(layoutData);

		GridLayout bcLayout = new GridLayout(hasGroup ? 3 : 2, false);
		bcLayout.marginHeight = 0;
		buttonComp.setLayout(bcLayout);
		GridData gridData;

		Button buttonAdd = new Button(buttonComp, SWT.PUSH);
		gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		buttonAdd.setLayoutData(gridData);
		Messages.setLanguageText(buttonAdd, "label.add.tag");
		buttonAdd.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				TagUIUtils.createManualTag(new TagReturner() {
					@Override
					public void returnedTags(Tag[] tags) {
						if (taggables == null) {
							return;
						}
						for (Tag tag : tags) {
							for (Taggable taggable : taggables) {
								tag.addTaggable(taggable);
							}
						}
					}
				});
			}
		});

		buttonExplain = new Button(buttonComp, SWT.PUSH);
		gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		buttonExplain.setLayoutData(gridData);
		Messages.setLanguageText(buttonExplain, "button.explain");
		buttonExplain.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				explain();
			}});

		if (hasGroup) {
			int layoutStyle = tagButtonsUI.getLayoutStyle();

			ToolBar toolBar = new ToolBar(buttonComp, SWT.NONE);
			gridData = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
			toolBar.setLayoutData(gridData);

			ToolItem buttonRowMode = new ToolItem(toolBar, SWT.RADIO);
			if (layoutStyle == SWT.HORIZONTAL) {
				buttonRowMode.setSelection(true);
			}
			ToolItem buttonColumnMode = new ToolItem(toolBar, SWT.RADIO);
			if (layoutStyle == SWT.VERTICAL) {
				buttonColumnMode.setSelection(true);
			}
			ToolItem buttonRowCompactMode = new ToolItem(toolBar, SWT.RADIO);
			if (layoutStyle == (SWT.HORIZONTAL | SWT.FILL)) {
				buttonRowCompactMode.setSelection(true);
			}

			ImageLoader.getInstance().setToolItemImage(buttonRowMode, "row_mode");
			buttonRowMode.addListener(SWT.Selection,
				event -> tagButtonsUI.setLayoutStyle(SWT.HORIZONTAL));

			ImageLoader.getInstance().setToolItemImage(buttonColumnMode, "column_mode");
			buttonColumnMode.addListener(SWT.Selection,
				event -> tagButtonsUI.setLayoutStyle(SWT.VERTICAL));

			ImageLoader.getInstance().setToolItemImage(buttonRowCompactMode, "row_compact_mode");
			buttonRowCompactMode.addListener(SWT.Selection,
				event -> tagButtonsUI.setLayoutStyle(SWT.HORIZONTAL | SWT.FILL));
		}

		///

		sc = new ScrolledComposite(mainComposite, SWT.V_SCROLL);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		sc.setLayoutData(gd);
		sc.setExpandHorizontal(true);
		sc.setExpandVertical(true);
		sc.getVerticalBar().setIncrement(16);

		Composite cTagComposite = new Composite(sc, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = layout.marginWidth = 0;
		cTagComposite.setLayout(layout);
		sc.setContent(cTagComposite);
		
		Composite tagArea = tagButtonsUI.buildTagGroup(listAllTags, cTagComposite,
			true, new TagButtonTrigger() {
				@Override
				public void tagButtonTriggered(TagCanvas tagCanvas, Tag tag, int stateMask, boolean longPress) {
					boolean doTag = !tagCanvas.isSelected();
					for (Taggable taggable : taggables) {
						if (doTag) {
							tag.addTaggable(taggable);
						} else {
							tag.removeTaggable(taggable);
						}
						tagCanvas.setSelected(doTag);
					}
				}

				@Override
				public Boolean tagSelectedOverride(Tag tag) {
					return null;
				}
			});
		tagArea.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

		
		sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				Point size = cTagComposite.computeSize(r.width, SWT.DEFAULT);
				sc.setMinSize(size);
			}
		});

		swt_updateFields();

		Rectangle r = sc.getClientArea();
		Point size = cTagComposite.computeSize(r.width, SWT.DEFAULT);
		sc.setMinSize(size);
	}

	private String getFullTitle() {
		return MessageText.getString("label.tags");
	}

	private void swt_updateFields() {

		if (mainComposite == null || mainComposite.isDisposed()) {
			return;
		}

		if (tagButtonsUI.updateFields(taggables)) {
			parent.layout();
		}
		
		boolean has_constraint = false;

		if ( taggables != null ){
						
			List<Tag> listAllTags = getTags();
					
			for ( Tag tag: listAllTags ){
				
				TagFeatureProperties	tfp = (TagFeatureProperties)tag;
				
				String[] constraint = tfp.getProperty( TagFeatureProperties.PR_CONSTRAINT ).getStringList();
				
				if ( constraint != null && constraint.length > 0 ){
					
					String s = constraint[0];
					
					if ( s.length() > 0 ){
						
						has_constraint = true;
						
						break;
					}
				}
			}
		}
		
		buttonExplain.setEnabled( has_constraint );
	}

	private List<Tag>
	getTags()
	{
		TagManager tm = TagManagerFactory.getTagManager();
		
		int[] tagTypesWanted = {
			TagType.TT_DOWNLOAD_MANUAL,
			//TagType.TT_DOWNLOAD_CATEGORY
		};
		
		List<Tag> listAllTags = new ArrayList<>();

		for (int tagType : tagTypesWanted) {

			TagType tt = tm.getTagType(tagType);
			List<Tag> tags = tt.getTags();
			listAllTags.addAll(tags);
		}
		
		return( listAllTags );
	}
	
	@Override
	public void tagTypeChanged(TagType tag_type) {
	}

	@Override
	public void tagEventOccurred(TagEvent event ) {
		int	type = event.getEventType();
		Tag	tag = event.getTag();
		if ( type == TagEvent.ET_TAG_ADDED ){
			tagAdded( tag );
		}else if ( type == TagEvent.ET_TAG_MEMBERSHIP_CHANGED || type == TagEvent.ET_TAG_METADATA_CHANGED ){
			tagChanged( tag );
		}else if ( type == TagEvent.ET_TAG_REMOVED ){
			tagRemoved( tag );
		}
	}

	public void tagAdded(Tag tag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				initialize();
			}
		});
	}

	public void tagChanged(final Tag changedTag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_updateFields();
			}
		});
	}

	public void tagRemoved(Tag tag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				initialize();
			}
		});
	}

	private void
	explain()
	{
		List<Tag> tags = getTags();
		
		TagUtils.sortTags( tags );
		
		StringBuilder content = new StringBuilder(1024);
		
		for ( Taggable t: taggables ){
			
			content.append( t.getTaggableName() + "\n\n" );
			
			for ( Tag tag: tags ){
				
				TagFeatureProperties	tfp = (TagFeatureProperties)tag;
				
				TagProperty tfp_constraint = tfp.getProperty( TagFeatureProperties.PR_CONSTRAINT );
				
				String[] constraint = tfp_constraint.getStringList();
				
				if ( constraint != null && constraint.length > 0 ){
					
					String s = constraint[0];
					
					if ( s.length() > 0 ){

						String details = tfp_constraint.explainTaggable( t );
						
						content.append( tag.getTagName(true) + " -> " + details + "\n" );
					}
				}
			}
			
			content.append( "\n" );
		}
		
		new TextViewerWindow(
				MessageText.getString( "label.details" ),
				null, content.toString(), false  );

	}
}
