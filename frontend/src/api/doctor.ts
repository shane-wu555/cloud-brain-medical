import { http } from './http';
export interface Department{id:string;name:string;description:string}
export interface Doctor{id:string;name:string;title:string;departmentId:string;departmentName:string;specialty:string;roleType:string}
export interface ScheduleTimeSlot{id:string;startTime:string;capacity:number;booked:number;locked:number;available:number}
export interface Schedule{id:string;doctorId:string;doctorName:string;departmentId:string;workDate:string;period:string;capacity:number;booked:number;locked:number;available:number;status:string;timeSlots?:ScheduleTimeSlot[]}
export async function getDepartments(){return(await http.get<Department[]>('/departments')).data;}
export async function getDoctors(departmentId?:string){return(await http.get<Doctor[]>('/doctors',{params:{departmentId}})).data;}
export async function getSchedules(params:{doctorId?:string;departmentId?:string}={}){return(await http.get<Schedule[]>('/schedules',{params})).data;}
export async function createSchedule(payload:{doctorId:string;departmentId:string;workDate:string;period:string;capacity:number}){return(await http.post<Schedule>('/schedules',payload)).data;}
export async function suspendSchedule(id:string,reason:string){return(await http.put<Schedule>(`/schedules/${id}/suspend`,{reason})).data;}
export async function reschedule(id:string,workDate:string,period:string){return(await http.put<Schedule>(`/schedules/${id}/reschedule`,{workDate,period})).data;}
export async function createDepartment(payload:{name:string;description:string}){return(await http.post<Department>('/departments',payload)).data;}
export async function createDoctor(payload:{name:string;title:string;departmentId:string;roleType:string;specialty:string}){return(await http.post<Doctor>('/doctors',payload)).data;}
