/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.core.speedmanager;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.DisplayFormatters;

public class
LimitToTextHelper
{
	String	msg_text_unknown;
	String	msg_text_estimate;
	String	msg_text_choke_estimate;
	String	msg_text_measured_min;
	String	msg_text_measured;
	String	msg_text_manual;
	String	msg_unlimited;

	String[]	setable_types;

	public LimitToTextHelper()
	{
		msg_text_unknown			= MessageText.getString( "SpeedView.stats.unknown" );
		msg_text_estimate			= MessageText.getString( "SpeedView.stats.estimate" );
		msg_text_choke_estimate	= MessageText.getString( "SpeedView.stats.estimatechoke" );
		msg_text_measured			= MessageText.getString( "SpeedView.stats.measured" );
		msg_text_measured_min		= MessageText.getString( "SpeedView.stats.measuredmin" );
		msg_text_manual			= MessageText.getString( "SpeedView.stats.manual" );

		msg_unlimited			= MessageText.getString( "ConfigView.unlimited" );

		setable_types =  new String[]{ "", msg_text_estimate, msg_text_measured, msg_text_manual };
	}

	public String[]
	getSettableTypes()
	{
		return( setable_types );
	}

	public String
	getSettableType(
		SpeedManagerLimitEstimate limit )
	{
		float type = limit.getEstimateType();

		String	text;

		if ( type == SpeedManagerLimitEstimate.TYPE_UNKNOWN){

			text = "";

		}else if ( type == SpeedManagerLimitEstimate.TYPE_MANUAL){

			text = msg_text_manual;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_MEASURED){

			text = msg_text_measured;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_MEASURED_MIN){

			text = msg_text_measured;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED){

			text = msg_text_estimate;

		}else{

			text = msg_text_estimate;
		}

		return( text );  	  }

	public String
	typeToText(
		float 	type )
	{
		String	text;

		if ( type == SpeedManagerLimitEstimate.TYPE_UNKNOWN){

			text = msg_text_unknown;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_MANUAL){

			text = msg_text_manual;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_MEASURED){

			text = msg_text_measured;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_MEASURED_MIN){

			text = msg_text_measured_min;

		}else if ( type == SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED){

			text = msg_text_choke_estimate;

		}else{

			text = msg_text_estimate;
		}

		return( text );
	}

	public float
	textToType(
		String	text )
	{
		if ( text.equals( msg_text_estimate )){

			return( SpeedManagerLimitEstimate.TYPE_ESTIMATED);

		}else if ( text.equals( msg_text_choke_estimate )){

			return( SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED);

		}else if ( text.equals( msg_text_measured )){

			return( SpeedManagerLimitEstimate.TYPE_MEASURED);

		}else if ( text.equals( msg_text_manual )){

			return( SpeedManagerLimitEstimate.TYPE_MANUAL);

		}else{

			return( SpeedManagerLimitEstimate.TYPE_UNKNOWN);
		}
	}

	public String
	getLimitText(
		SpeedManagerLimitEstimate	limit )
	{
		float type = limit.getEstimateType();

		String	text = typeToText( type );

		int	l = limit.getBytesPerSec();

		if ( l == 0 ){

			return( msg_unlimited + " (" + text + ")");

		}else{

			return( DisplayFormatters.formatByteCountToKiBEtcPerSec( l ) + " (" + text + ")");
		}
	}

	public String
	getUnlimited()
	{
		return( msg_unlimited );
	}
}
