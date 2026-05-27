import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProcessedDocument } from '../models/document.model';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private apiUrl = 'http://localhost:8082/api/documents';

  constructor(private http: HttpClient) { }

  getDocuments(search?: string, startDate?: string, endDate?: string): Observable<ProcessedDocument[]> {
    let params = new HttpParams();
    if (search) {
      params = params.set('search', search);
    }
    if (startDate) {
      params = params.set('startDate', startDate);
    }
    if (endDate) {
      params = params.set('endDate', endDate);
    }
    return this.http.get<ProcessedDocument[]>(this.apiUrl, { params });
  }

  getUniqueSenders(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/senders`);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  resendDocument(id: string, targetEmail: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/resend`, targetEmail);
  }
}