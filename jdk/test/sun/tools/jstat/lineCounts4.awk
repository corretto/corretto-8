#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT   
#  0.00  96.88  66.55   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  71.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  73.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  73.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  73.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  75.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  75.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  77.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  77.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  0.00  96.88  77.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003
#  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT   
#  0.00  96.88  79.58   2.34  77.78  68.02      1    0.003     0    0.000    0.003

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	    datalines2=0;
        }

/^  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*([0-9]+\.[0-9]+)|-[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    if (headerlines == 2) {
	        datalines2++;
	    }
	    datalines++;
	}

	{ totallines++; print $0 }

END	{ 
	    if ((headerlines == 2) && (datalines == 11) && (totallines == 13) && (datalines2 == 1)) {
	        exit 0
	    } else {
	        exit 1
	    }
	}
