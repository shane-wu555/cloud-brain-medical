import { http } from './http';
export interface Appointment {
  id: string;
  businessNo: string;
  slotId?: string;
  scheduleId?: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  departmentId: string;
  departmentName: string;
  roomName?: string;
  visitDate: string;
  period: string;
  startTime?: string;
  source: string;
  status: string;
  paymentStatus: string;
  paymentMethod?: string;
  triageSummary: string;
  riskLevel: string;
  recommendedDepartmentId?: string;
  queueNumber: number;
  missedCount: number;
  createdAt: string;
}
export async function getAppointments(params:{doctorId?:string;patientId?:string;status?:string;includeRoom?:boolean}={}){return(await http.get<Appointment[]>('/appointments',{params})).data;}
export async function skipAppointment(id:string){return(await http.post<Appointment>(`/appointments/${id}/skip`)).data;}
export async function updateAppointmentStatus(id:string,status:string){return(await http.patch<Appointment>(`/appointments/${id}/status`,{status})).data;}
export async function createOfflineAppointment(payload:Record<string,unknown>){return(await http.post<Appointment>('/appointments/offline',payload)).data;}
export async function getTodayQueue(){return(await http.get<Appointment[]>('/appointments/queue/today')).data;}
export async function callAppointment(id:string){return(await http.post<Appointment>(`/appointments/${id}/call`)).data;}
export async function startAppointment(id:string){return(await http.post<Appointment>(`/appointments/${id}/start`)).data;}
export async function cancelAppointment(id:string){return(await http.post<Appointment>(`/appointments/${id}/cancel`)).data;}
