import os
import subprocess

def get_sim_packages(vehicle_id, s3_bucket, s3_key, dst_file_loc):
    try:
        aws_cp_cmd = "aws s3 sync "  + f"s3://{s3_bucket}/{s3_key}" +  " " + dst_file_loc +  str(vehicle_id)
        print(aws_cp_cmd)
        subprocess.check_output(aws_cp_cmd, shell=True)
    except Exception as e:
        print ("Failure in downloading simulation package for Vehicle ID  {} as {}, {}".format(vehicle_id, e))
        raise


if __name__=='__main__':
    """
        For Testing only
    """   
    os.environ['SIM_PKG_URL'] = 's3_buck/'
    os.environ['VEHICLE_ID'] = '[101, 102, 104, 105]'
    get_sim_packages()
