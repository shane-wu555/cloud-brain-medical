import { http } from './http';
export interface Department{id:string;name:string;description:string}
export interface Doctor{id:string;name:string;title:string;departmentId:string;departmentName:string;specialty:string;roleType:string}
export interface Schedule{id:string;doctorId:string;doctorName:string;departmentId:string;workDate:string;period:string;capacity:number;booked:number}
export async function getDepartments(){return(await http.get<Department[]>('/departments')).data;}
export async function getDoctors(departmentId?:string){return(await http.get<Doctor[]>('/doctors',{params:{departmentId}})).data;}
export async function getSchedules(params:{doctorId?:string;departmentId?:string}={}){return(await http.get<Schedule[]>('/schedules',{params})).data;}
export async function createSchedule(payload:{doctorId:string;departmentId:string;workDate:string;period:string;capacity:number}){return(await http.post<Schedule>('/schedules',payload)).data;}
