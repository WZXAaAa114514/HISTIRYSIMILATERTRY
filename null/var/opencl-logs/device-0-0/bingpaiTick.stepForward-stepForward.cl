#pragma OPENCL EXTENSION cl_khr_fp64 : enable  
#pragma OPENCL EXTENSION cl_khr_fp16 : enable  
#pragma OPENCL EXTENSION cl_khr_int64_base_atomics : enable
__kernel void stepForward(__global long *_kernel_context, __constant uchar *_constant_region, __local uchar *_local_region, __global long *_atomics, __global uchar *arg0, __global uchar *arg1, __global uchar *arg2, __global uchar *arg3, __global uchar *arg4, __global uchar *arg5, __global uchar *arg6)
{
  ulong ul_0, ul_1, ul_2, ul_3, ul_4, ul_5, ul_6, ul_13, ul_15, ul_17, ul_18, ul_19, ul_21, ul_33; 
  long i_7, i_8, i_9, i_30, i_31, i_32, i_52;
  long l_10, l_11, l_12; 
  double d_14, d_16, d_20, d_22, d_23, d_24, d_25, d_26, d_27, d_28, d_29, d_34, d_35, d_37, d_38, d_39, d_40, d_41, d_42, d_43, d_44, d_45, d_46, d_51, d_48, d_49, d_50; 
  bool b_36, b_47; 

  // BLOCK 0
  ul_0  =  (ulong) arg0;
  ul_1  =  (ulong) arg1;
  ul_2  =  (ulong) arg2;
  ul_3  =  (ulong) arg3;
  ul_4  =  (ulong) arg4;
  ul_5  =  (ulong) arg5;
  ul_6  =  (ulong) arg6;
  i_7  =  get_global_size(0);
  i_8  =  get_global_id(0);
  // BLOCK 1 MERGES [0 13 ]
  i_9  =  i_8;
  for(;i_9 < 102;)
  {
    // BLOCK 2
    l_10  =  (long) i_9;
    l_11  =  l_10 << 3;
    l_12  =  l_11 + 16L;
    ul_13  =  ul_0 + l_12;
    d_14  =  *((__global double *) ul_13);
    ul_15  =  ul_1 + l_12;
    d_16  =  *((__global double *) ul_15);
    ul_17  =  ul_5 + l_12;
    *((__global double *) ul_17)  =  d_14;
    ul_18  =  ul_6 + l_12;
    *((__global double *) ul_18)  =  d_16;
    ul_19  =  ul_2 + l_12;
    d_20  =  *((__global double *) ul_19);
    ul_21  =  ul_3 + l_12;
    d_22  =  *((__global double *) ul_21);
    d_23  =  d_22 - d_16;
    d_24  =  d_20 - d_14;
    d_25  =  d_24 + 180.0;
    d_26  =  d_25 % 360.0;
    d_27  =  d_26 + 360.0;
    d_28  =  d_27 % 360.0;
    d_29  =  d_28 - 180.0;
    i_30  =  i_7 + i_9;
    i_31  =  isequal(d_29, 0.0);
    if(i_31 == 1)
    {
      // BLOCK 3
      i_32  =  isequal(d_23, 0.0);
      if(i_32 == 1)
      {
        // BLOCK 4
      }  // B3
      else
      {
        // BLOCK 5
        // BLOCK 7 MERGES [6 5 ]
        ul_33  =  ul_4 + l_12;
        d_34  =  *((__global double *) ul_33);
        d_35  =  d_34 / 3600.0;
        b_36  =  !isgreaterequal(0.0, d_35);
        if(b_36)
        {
          // BLOCK 8
          d_37  =  d_23 * 111.32;
          d_38  =  d_16 + d_22;
          d_39  =  d_38 * 0.5;
          d_40  =  d_39 * 0.017453292519943295;
          d_41  =  native_cos(d_40);
          d_42  =  d_29 * 111.32;
          d_43  =  d_41 * d_42;
          d_44  =  d_43 * d_43;
          d_45  =  fma(d_37, d_37, d_44);
          d_46  =  native_sqrt(d_45);
          b_47  =  !isgreaterequal(d_35, d_46);
          if(b_47)
          {
            // BLOCK 9
            d_48  =  d_35 / d_46;
            d_49  =  fma(d_29, d_48, d_14);
            *((__global double *) ul_13)  =  d_49;
            d_50  =  fma(d_23, d_48, d_16);
            *((__global double *) ul_15)  =  d_50;
          }  // B8
          else
          {
            // BLOCK 10
            // BLOCK 12 MERGES [11 10 ]
            d_51  =  d_29 + d_14;
            *((__global double *) ul_13)  =  d_51;
            *((__global double *) ul_15)  =  d_22;
          }  // B8
        }  // B7
        else
        {
          // BLOCK 11
          // BLOCK 12 MERGES [11 10 ]
          d_51  =  d_29 + d_14;
          *((__global double *) ul_13)  =  d_51;
          *((__global double *) ul_15)  =  d_22;
        }  // B7
      }  // B3
    }  // B2
    else
    {
      // BLOCK 6
      // BLOCK 7 MERGES [6 5 ]
      ul_33  =  ul_4 + l_12;
      d_34  =  *((__global double *) ul_33);
      d_35  =  d_34 / 3600.0;
      b_36  =  !isgreaterequal(0.0, d_35);
      if(b_36)
      {
        // BLOCK 8
        d_37  =  d_23 * 111.32;
        d_38  =  d_16 + d_22;
        d_39  =  d_38 * 0.5;
        d_40  =  d_39 * 0.017453292519943295;
        d_41  =  native_cos(d_40);
        d_42  =  d_29 * 111.32;
        d_43  =  d_41 * d_42;
        d_44  =  d_43 * d_43;
        d_45  =  fma(d_37, d_37, d_44);
        d_46  =  native_sqrt(d_45);
        b_47  =  !isgreaterequal(d_35, d_46);
        if(b_47)
        {
          // BLOCK 9
          d_48  =  d_35 / d_46;
          d_49  =  fma(d_29, d_48, d_14);
          *((__global double *) ul_13)  =  d_49;
          d_50  =  fma(d_23, d_48, d_16);
          *((__global double *) ul_15)  =  d_50;
        }  // B8
        else
        {
          // BLOCK 10
          // BLOCK 12 MERGES [11 10 ]
          d_51  =  d_29 + d_14;
          *((__global double *) ul_13)  =  d_51;
          *((__global double *) ul_15)  =  d_22;
        }  // B8
      }  // B7
      else
      {
        // BLOCK 11
        // BLOCK 12 MERGES [11 10 ]
        d_51  =  d_29 + d_14;
        *((__global double *) ul_13)  =  d_51;
        *((__global double *) ul_15)  =  d_22;
      }  // B7
    }  // B2
    // BLOCK 13 MERGES [4 12 9 ]
    i_52  =  i_30;
    i_9  =  i_52;
  }  // B1
  // BLOCK 14
  return;
}  //  kernel
