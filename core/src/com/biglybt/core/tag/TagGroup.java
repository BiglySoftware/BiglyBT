package com.biglybt.core.tag;

import java.io.File;
import java.util.List;

public interface 
TagGroup
{
	public String
	getName();
	
	public void
	setName(
		String	name );
	
	public boolean
	isExclusive();
	
	public void
	setExclusive(
		boolean		b );
	
	public void
	setRootMoveOnAssignLocation(
		File		loc );
	
	public File
	getRootMoveOnAssignLocation();
	
	public void
	setColor(
		int[]		rgb );

	public int[]
	getColor();
	
	public TagType
	getTagType();
	
	public List<Tag>
	getTags();
	
	public void
	addListener(
		TagGroupListener	l,
		boolean				fire_for_existing );
	
	public void
	removeListener(
		TagGroupListener	l );
}
