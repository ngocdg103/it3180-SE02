package com.bluemoon.models;

/** Payment status of a monthly charge (EP-03 section 3.1). US-007 only creates CHUA_DONG; US-008 moves it. */
public enum ChargeStatus { CHUA_DONG, DONG_THIEU, DA_DONG_DU, DONG_THUA }
