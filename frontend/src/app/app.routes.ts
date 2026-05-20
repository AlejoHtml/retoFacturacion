import { Routes } from '@angular/router';
import { DocumentListComponent } from './components/document-list/document-list.component';

export const routes: Routes = [
  { path: '', component: DocumentListComponent },
  { path: '**', redirectTo: '' }
];
