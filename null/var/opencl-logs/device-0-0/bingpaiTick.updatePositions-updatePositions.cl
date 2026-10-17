#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
#pragma OPENCL EXTENSION cl_khr_fp16 : enable  
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void updatePositions(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global long *_atomics, __global uchar *arg0, __global uchar *arg1, __global uchar *arg2, __global uchar *arg3, __global uchar *arg4, __global uchar *arg5, __global uchar *arg6, __global uchar *arg7, __global uchar *arg8, __global uchar *arg9)
{
  ulong ul_0, ul_1, ul_2, ul_3, ul_4, ul_5, ul_6, ul_7, ul_8, ul_9, ul_10, ul_18, ul_20, ul_22, ul_23, ul_24, ul_26, ul_36, ul_37, ul_40; 
  long i_11, i_12, i_13, i_14, i_35, i_38, i_39, i_61;
  long l_15, l_16, l_17; 
  double d_19, d_21, d_25, d_27, d_28, d_29, d_30, d_31, d_32, d_33, d_34, d_41, d_42, d_43, d_44, d_46, d_47, d_48, d_49, d_50, d_51, d_52, d_53, d_54, d_55, d_60, d_57, d_58, d_59; 
  bool b_45, b_56; 

  // BLOCK 0
  ul_0  =  (ulong) arg0;
  ul_1  =  (ulong) arg1;
  ul_2  =  (ulong) arg2;
  ul_3  =  (ulong) arg3;
  ul_4  =  (ulong) arg4;
  ul_5  =  (ulong) arg5;
  ul_6  =  (ulong) arg6;
  ul_7  =  (ulong) arg7;
  ul_8  =  (ulong) arg8;
  ul_9  =  (ulong) arg9;
  ul_10  =  ul_5 + 16L;
  i_11  =  *((__global long *) ul_10);
  i_12  =  get_global_size(0);
  i_13  =  get_global_id(0);
  // BLOCK 1 MERGES [0 13 ]
  i_14  =  i_13;
  for(;i_14 < 102;)
  {
    // BLOCK 2
    l_15  =  (long) i_14;
    l_16  =  l_15 << 3;
    l_17  =  l_16 + 16L;
    ul_18  =  ul_0 + l_17;
    d_19  =  *((__global double *) ul_18);
    ul_20  =  ul_1 + l_17;
    d_21  =  *((__global double *) ul_20);
    ul_22  =  ul_6 + l_17;
    *((__global double *) ul_22)  =  d_19;
    ul_23  =  ul_7 + l_17;
    *((__global double *) ul_23)  =  d_21;
    ul_24  =  ul_2 + l_17;
    d_25  =  *((__global double *) ul_24);
    ul_26  =  ul_3 + l_17;
    d_27  =  *((__global double *) ul_26);
    d_28  =  d_27 - d_21;
    d_29  =  d_25 - d_19;
    d_30  =  d_29 + 180.0;
    d_31  =  d_30 % 360.0;
    d_32  =  d_31 + 360.0;
    d_33  =  d_32 % 360.0;
    d_34  =  d_33 - 180.0;
    i_35  =  i_12 + i_14;
    ul_36  =  ul_9 + l_17;
    ul_37  =  ul_8 + l_17;
    i_38  =  isequal(d_34, 0.0);
    if(i_38 == 1)
    {
      // BLOCK 3
      i_39  =  isequal(d_28, 0.0);
      if(i_39 == 1)
      {
        // BLOCK 4
        *((__global double *) ul_37)  =  d_19;
        *((__global double *) ul_36)  =  d_21;
      }  // B3
      else
      {
        // BLOCK 5
        // BLOCK 7 MERGES [6 5 ]
        ul_40  =  ul_4 + l_17;
        d_41  =  *((__global double *) ul_40);
        d_42  =  d_41 / 3600.0;
        d_43  =  (double) i_11;
        d_44  =  d_42 * d_43;
        b_45  =  !isgreaterequal(0.0, d_44);
        if(b_45)
        {
          // BLOCK 8
          d_46  =  d_28 * 111.32;
          d_47  =  d_21 + d_27;
          d_48  =  d_47 * 0.5;
          d_49  =  d_48 * 0.017453292519943295;
          d_50  =  native_cos(d_49);
          d_51  =  d_34 * 111.32;
          d_52  =  d_50 * d_51;
          d_53  =  d_52 * d_52;
          d_54  =  fma(d_46, d_46, d_53);
          d_55  =  native_sqrt(d_54);
          b_56  =  !isgreaterequal(d_44, d_55);
          if(b_56)
          {
            // BLOCK 9
            d_57  =  d_44 / d_55;
            d_58  =  fma(d_34, d_57, d_19);
            *((__global double *) ul_37)  =  d_58;
            d_59  =  fma(d_28, d_57, d_21);
            *((__global double *) ul_36)  =  d_59;
          }  // B8
          else
          {
            // BLOCK 10
            // BLOCK 12 MERGES [11 10 ]
            d_60  =  d_34 + d_19;
            *((__global double *) ul_37)  =  d_60;
            *((__global double *) ul_36)  =  d_27;
          }  // B8
        }  // B7
        else
        {
          // BLOCK 11
          // BLOCK 12 MERGES [11 10 ]
          d_60  =  d_34 + d_19;
          *((__global double *) ul_37)  =  d_60;
          *((__global double *) ul_36)  =  d_27;
        }  // B7
      }  // B3
    }  // B2
    else
    {
      // BLOCK 6
      // BLOCK 7 MERGES [6 5 ]
      ul_40  =  ul_4 + l_17;
      d_41  =  *((__global double *) ul_40);
      d_42  =  d_41 / 3600.0;
      d_43  =  (double) i_11;
      d_44  =  d_42 * d_43;
      b_45  =  !isgreaterequal(0.0, d_44);
      if(b_45)
      {
        // BLOCK 8
        d_46  =  d_28 * 111.32;
        d_47  =  d_21 + d_27;
        d_48  =  d_47 * 0.5;
        d_49  =  d_48 * 0.017453292519943295;
        d_50  =  native_cos(d_49);
        d_51  =  d_34 * 111.32;
        d_52  =  d_50 * d_51;
        d_53  =  d_52 * d_52;
        d_54  =  fma(d_46, d_46, d_53);
        d_55  =  native_sqrt(d_54);
        b_56  =  !isgreaterequal(d_44, d_55);
        if(b_56)
        {
          // BLOCK 9
          d_57  =  d_44 / d_55;
          d_58  =  fma(d_34, d_57, d_19);
          *((__global double *) ul_37)  =  d_58;
          d_59  =  fma(d_28, d_57, d_21);
          *((__global double *) ul_36)  =  d_59;
        }  // B8
        else
        {
          // BLOCK 10
          // BLOCK 12 MERGES [11 10 ]
          d_60  =  d_34 + d_19;
          *((__global double *) ul_37)  =  d_60;
          *((__global double *) ul_36)  =  d_27;
        }  // B8
      }  // B7
      else
      {
        // BLOCK 11
        // BLOCK 12 MERGES [11 10 ]
        d_60  =  d_34 + d_19;
        *((__global double *) ul_37)  =  d_60;
        *((__global double *) ul_36)  =  d_27;
      }  // B7
    }  // B2
    // BLOCK 13 MERGES [4 12 9 ]
    i_61  =  i_35;
    i_14  =  i_61;
  }  // B1
  // BLOCK 14
  return;
}  //  kernel
