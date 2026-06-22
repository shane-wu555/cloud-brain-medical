import { http } from './http';
export interface Appointment { id:string;scheduleId:string;patientId:string;patientName:string;doctorId:string;doctorName:string;departmentId:string;departmentName:string;visitDate:string;period:string;source:string;status:string;paymentStatus:string;paymentMethod?:string;triageSummary:string;riskLevel:string;recommendedDepartmentId?:string;queueNumber:number;missedCount:number;createdAt:string }
export async function getAppointments(params:{doctorId?:string;patientId?:string;status?:string}={}){return(await http.get<Appointment[]>('/appointments',{params})).data;}
export async function skipAppointment(id:string){return(await http.post<Appointment>(`/appointments/${id}/skip`)).data;}
export async function updateAppointmentStatus(id:string,status:string){return(await http.patch<Appointment>(`/appointments/${id}/status`,{status})).data;}
