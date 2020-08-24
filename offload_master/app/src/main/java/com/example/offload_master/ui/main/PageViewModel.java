package com.example.offload_master.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;

import java.util.Arrays;

public class PageViewModel extends ViewModel {

    private MutableLiveData<Integer> mIndex = new MutableLiveData<>();
    private LiveData<String> mText = Transformations.map(mIndex, new Function<Integer, String>() {
        @Override
        public String apply(Integer input) {
            Gson gson = new Gson();
//            Intent intent = this.getIntent();
            int[][] matA = matrixGen();//Integer.parseInt(intent.getStringExtra("A"));
            int[][] matB = matrixGen();//Integer.parseInt(intent.getStringExtra("B"));
            int[][] matResult = matMultiply(matA,matB);//Integer.parseInt(intent.getStringExtra("C"));

            if (input == 1){
                //Will show matrix A
                return "Matrix A \n" + printHelp(matA);
            }else if (input == 2){
                //Will show matrix B
                return "Matrix B \n" + printHelp(matB);
            }else{
                //Will show matrix A*B
                return "Result \n " + printHelp(matResult);
            }

        }
    });

    public void setIndex(int index) {
        mIndex.setValue(index);
    }

    public LiveData<String> getText() {
        return mText;
    }

    public int[][] matrixGen(){
        int[][] matrix = new int[50][50];
        for (int i=0; i<matrix.length; i++) {
            for (int j=0; j<matrix[i].length; j++) {
                matrix[i][j] = (int) (Math.random()*100);
            }
        }
        return matrix;
    }

    public int[][] matMultiply(int[][] A, int[][] B){
        int[][] matrix = new int[50][50];
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < B[0].length; j++) {
                for (int k = 0; k < A[0].length; k++) {
                    matrix[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return matrix;
    }

    public String printHelp(int[][] A){
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[i].length; j++) {
                System.out.print(A[i][j] + " ");
                result.append(A[i][j]).append(" ");
            }
            result.append("\n");
        }
        return result.toString();
    }

}