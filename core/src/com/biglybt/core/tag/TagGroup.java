package com.biglybt.core.tag;

import java.util.List;

public interface 
TagGroup
{
	public String
	getName();
	
	public boolean
	isExclusive();
	
	public void
	setExclusive(
		boolean		b );
	
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
