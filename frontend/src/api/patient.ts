import { http } from './http';
export interface PatientProfile{userId:string;phone:string;name:string;realNameVerified:boolean;idNumber?:string}
export async function searchPatientByIdNumber(idNumber:string){return(await http.get<PatientProfile[]>('/patients',{params:{idNumber}})).data;}
export async function searchPatientByPhone(phone:string){return(await http.get<PatientProfile[]>('/patients',{params:{phone}})).data;}
export async function createOfflinePatient(idType:string,idNumber:string,name:string,phone?:string){
  return(await http.post<PatientProfile>('/patients/offline',{idType,idNumber,name,phone})).data;
}
