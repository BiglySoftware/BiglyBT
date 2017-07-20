/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.biglybt.core.neuronal;

public class NeuralNetworkLayer {

	final int numberOfNodes;
	double[][] weights;
	double[][] weightChanges;
	double[] neuronValues;
	double[] desiredValues;
	double[] errors;
	double[] biasWeights;
	double[] biasValues;

	double learningRate;
	final boolean linearOutput;
	boolean useMomentum;
	double momentumFactor;

	NeuralNetworkLayer parentLayer;
	NeuralNetworkLayer childLayer;

	ActivationFunction activationFunction;



	public NeuralNetworkLayer(int numberOfNodes) {

		this.numberOfNodes = numberOfNodes;

		linearOutput = false;
		useMomentum = false;
		momentumFactor = 0.9;
	}

	public void initialize(NeuralNetworkLayer parentLayer,NeuralNetworkLayer childLayer) {

		neuronValues = new double[numberOfNodes];
		desiredValues = new double[numberOfNodes];
		errors = new double[numberOfNodes];
		this.parentLayer = parentLayer;

		if(childLayer != null) {
			this.childLayer = childLayer;
			weights = new double[numberOfNodes][childLayer.getNumberOfNodes()];
			weightChanges = new double[numberOfNodes][childLayer.getNumberOfNodes()];
			biasValues = new double[childLayer.getNumberOfNodes()];
			biasWeights = new double[childLayer.getNumberOfNodes()];

			for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
				biasValues[j] = -1;
				biasWeights[j] = 0;
			}
		}
	}

	public void randomizeWeights() {
		for(int i = 0 ; i < numberOfNodes ; i++) {
			for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
				weights[i][j] = Math.random() * 2 - 1;
			}
		}

		for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
			biasWeights[j] = Math.random() * 2 - 1;
		}
	}

	public void calculateNeuronValues() {
		if(parentLayer != null) {
			for(int j = 0 ; j < numberOfNodes ; j++) {
				double x = 0;

				for(int i = 0 ; i < parentLayer.getNumberOfNodes() ; i++) {
					x += parentLayer.neuronValues[i] * parentLayer.weights[i][j];
				}

				x+= parentLayer.biasValues[j] * parentLayer.biasWeights[j];

				if(childLayer == null && linearOutput) {
					neuronValues[j] = x;
				} else {
					neuronValues[j] = activationFunction.getValueFor(x);
				}
			}
		}
	}

	public void calculateErrors() {

		if(childLayer == null) {
			// output layer
			for(int i = 0 ; i < numberOfNodes ; i++) {
				errors[i] = (desiredValues[i] - neuronValues[i]) * activationFunction.getDerivedFunctionValueFor(neuronValues[i]);
			}
		} else if(parentLayer == null) {
			//input layer
			for(int i = 0 ; i < numberOfNodes ; i++) {
				errors[i] = 0.0;
			}
		} else {
			// hidden layer
			for(int i = 0 ; i < numberOfNodes ; i++) {
				double sum = 0;
				for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
					sum += childLayer.errors[j] * weights[i][j];
				}
				errors[i] = sum * activationFunction.getDerivedFunctionValueFor(neuronValues[i]);
			}
		}

	}

	public void adjustWeights() {
		if(childLayer != null) {
			for(int i = 0 ; i < numberOfNodes ; i++) {
				for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
					double dw = learningRate * childLayer.errors[j] * neuronValues[i];
					if(useMomentum) {
						weights[i][j] += dw + momentumFactor * weightChanges[i][j];
						weightChanges[i][j] = dw;
					} else {
						weights[i][j] += dw;
					}
				}
			}

			for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
				biasWeights[j] += learningRate * childLayer.errors[j] * biasValues[j];
			}
		}
	}


	public int getNumberOfNodes() {
		return numberOfNodes;
	}

	public void setActivationFunction(ActivationFunction activationFunction) {
		this.activationFunction = activationFunction;
	}

	public void setMomentum(boolean useMomentum,double factor) {
		this.useMomentum = useMomentum;
		this.momentumFactor = factor;
	}

	public void setLearningRate(double rate) {
		this.learningRate = rate;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(childLayer != null) {
			for(int j = 0 ; j < childLayer.getNumberOfNodes() ; j++) {
				sb.append(j);
				sb.append("\t> ");
				for(int i = 0 ; i < numberOfNodes ; i++) {
					sb.append(i);
					sb.append(":");
					sb.append(weights[i][j]);
					sb.append("\t");
				}
				sb.append("\n");
			}
		}

		return sb.toString();
	}

}
