import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../services/document.service';
import { ProcessedDocument } from '../../models/document.model';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './document-list.component.html',
  styleUrl: './document-list.component.scss'
})
export class DocumentListComponent implements OnInit {
  documents: ProcessedDocument[] = [];
  searchTerm: string = '';
  loading: boolean = false;
  searched: boolean = false;

  constructor(private documentService: DocumentService) {}

  ngOnInit(): void {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.loading = true;
    this.documentService.getDocuments(this.searchTerm).subscribe({
      next: (data) => {
        this.documents = data;
        this.loading = false;
        this.searched = true;
      },
      error: (err) => {
        console.error('Error loading documents', err);
        this.loading = false;
        this.searched = true;
      }
    });
  }

  onSearch(): void {
    this.loadDocuments();
  }

  deleteDoc(id: string): void {
    if (confirm('¿Está seguro de eliminar este documento?')) {
      this.documentService.deleteDocument(id).subscribe({
        next: () => {
          this.loadDocuments();
        },
        error: (err) => {
          console.error('Error deleting document', err);
          alert('Error al eliminar el documento');
        }
      });
    }
  }

  resendDoc(id: string): void {
    const email = prompt('Ingrese el correo de destino:');
    if (email) {
      this.documentService.resendDocument(id, email).subscribe({
        next: () => {
          alert('Correo enviado exitosamente');
        },
        error: (err) => {
          console.error('Error resending document', err);
          alert('Error al enviar el correo');
        }
      });
    }
  }

  getObjectKeys(obj: any): string[] {
    return obj ? Object.keys(obj) : [];
  }
}