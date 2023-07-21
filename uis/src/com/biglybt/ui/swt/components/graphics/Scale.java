/*
 * File    : Scale.java
 * Created : 15 d�c. 2003}
 * By      : Olivier
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
package com.biglybt.ui.swt.components.graphics;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;

/**
 * @author Olivier
 *
 */
public class Scale {

  private boolean wantBinary;
  private boolean useSI;

  //The target number of pixels per scale level
  private int pixelsPerLevel = 50;

  //The max value
  private int max = 1;

  //The displayed number of levels
  private int nbLevels;

  //The computed (displayed max)
  private int displayedMax;


  //The number of pixels
  private int nbPixels = 1;


  boolean	isSIIECSensitive;

  private int[] scaleValues = {};
	private ParameterListener parameterListener;

	public
  Scale()
  {
	  this( true );
  }

  public
  Scale(
	boolean	_isSIIECSensitive )
  {
	  isSIIECSensitive	= _isSIIECSensitive;

	  parameterListener = new ParameterListener() {
		  @Override
		  public void parameterChanged(String name) {
			  wantBinary = COConfigurationManager.getBooleanParameter("ui.scaled.graphics.binary.based");

			  boolean wantSI = COConfigurationManager.getBooleanParameter("config.style.useSIUnits");
			  boolean forceSI = COConfigurationManager.getBooleanParameter("config.style.forceSIValues");

			  useSI = wantSI || forceSI;
		  }
	  };
		COConfigurationManager.addWeakParameterListener(parameterListener, true,
				"ui.scaled.graphics.binary.based", "config.style.useSIUnits",
				"config.style.forceSIValues");
  }

  public void
  dispose()
  {
	  COConfigurationManager.removeWeakParameterListener( parameterListener,
				"ui.scaled.graphics.binary.based", "config.style.useSIUnits",
				"config.style.forceSIValues");
  }
  
  public boolean
  isSIIECSensitive()
  {
	  return( isSIIECSensitive );
  }

  public void setMax(int max) {
    this.max = max;
    if(max < 1)
      max = 1;
    computeValues();
  }

  public int getMax() {
    return this.max;
  }

  public void setNbPixels(int nbPixels) {
    this.nbPixels = nbPixels;
    if(nbPixels < 1)
      nbPixels = 1;
    computeValues();
  }

  private void computeValues() {
    int targetNbLevels = nbPixels / pixelsPerLevel;
    if(targetNbLevels < 1)
      targetNbLevels = 1;
    double scaleFactor = max / targetNbLevels;
    long powFactor = 1;


    int 	scaleThing 	= wantBinary?2:10;
    double 	scaleMax 	= wantBinary?4:5;


    while(scaleFactor >= scaleThing) {
      powFactor = scaleThing * powFactor;
      scaleFactor = scaleFactor / scaleThing;
    }


    if(scaleFactor >= scaleMax){
      scaleFactor = scaleMax;
    }else if(scaleFactor >= 2){
      scaleFactor = scaleMax/2;
    }else{
      scaleFactor = 1;
    }

    long increment = (long)( scaleFactor * powFactor );

    if ( isSIIECSensitive ){

	    	/*
	    	 * Problem is that when using SI units we render 1000B as 1K, 2000B as 2K etc so we have to adjust
	    	 * the increment appropriately from a 1000 based value to a 1024 based value and vice-versa
	    	 */

    	int	divBy 	= 0;
    	int	multBy	= 0;

    	if ( useSI && !wantBinary ){

    		divBy 		= 1000;
    		multBy		= 1024;

    	}else if ( !useSI && wantBinary ){

    		divBy 		= 1024;
    		multBy		= 1000;
    	}

    	if ( divBy > 0 ){

	    	long	temp 	= increment;
	    	int		pow		= -1;

	    	while( temp > 0 ){

	    		temp = temp / divBy;
	    		pow++;
	    	}

	    	long	temp2 = 1;
	    	long	temp3 = 1;

	    	for ( int i=0;i<pow;i++){

	    		temp2 *= multBy;
	    		temp3 *= divBy;
	    	}

	    	increment = (long)((((double)increment)/temp3)*temp2);
    	}
    }

    nbLevels = (int)(max / ( increment) + 1);
    displayedMax = (int)( increment * nbLevels );


    int[] result = new int[nbLevels+1];
    for(int i = 0 ; i < nbLevels + 1 ; i++) {
      result[i] = (int)( i * increment );
    }

    scaleValues = result;
  }


  public int[] getScaleValues() {

    return( scaleValues );
  }

  public int getScaledValue(int value) {
    return(int)( ((long)value * nbPixels) / displayedMax );
  }

}
