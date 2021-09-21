package com.biglybt.core.tag;

public interface 
TagGroupListener
{
	public void
	tagAdded(
		TagGroup	group,
		Tag			tag );
	
	public void
	tagRemoved(
		TagGroup	group,
		Tag			tag );
	
	public default void
	groupChanged(
		TagGroup		group )
	{
	}
}
