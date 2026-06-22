import { http } from './http';
export interface PatientProfile{userId:string;phone:string;name:string;realNameVerified:boolean}
export async function searchPatients(phone:string){return(await http.get<PatientProfile[]>('/patients',{params:{phone}})).data;}
export async function createOfflinePatient(phone:string,name:string){return(await http.post<PatientProfile>('/patients/offline',{phone,name})).data;}
