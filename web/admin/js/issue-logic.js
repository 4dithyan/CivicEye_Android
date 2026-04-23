// Shared Issue Management Logic for Admin
let issues = [], staff = [], locations = [];
let map;

async function loadData(renderCallback) {
    // Static Data
    const lSnap = await db.collection('locations').get();
    locations = lSnap.docs.map(d => ({ ...d.data(), id: d.id }));

    // Real-time Issues
    db.collection('issues').orderBy('createdAt', 'desc').onSnapshot(snap => {
        issues = snap.docs.map(d => ({ ...d.data(), id: d.id }));
        if (renderCallback) renderCallback();
    });

    // Real-time Staff
    db.collection('users').where('role', '==', 'staff').onSnapshot(snap => {
        staff = snap.docs.map(d => ({ ...d.data(), id: d.id }));
    });
}

function getStatusBadge(s) {
    if (s === 'pending') return 'badge-warning';
    if (s === 'in_progress') return 'badge-info';
    if (s === 'resolved' || s === 'verified') return 'badge-success';
    return 'badge-danger';
}

async function openIssue(id) {
    const i = issues.find(x => x.id === id);
    if (!i) return;

    // Fetch Reporter Phone
    let reporterPhone = 'Not available';
    if (i.reporterId) {
        try {
            const uDoc = await db.collection('users').doc(i.reporterId).get();
            if (uDoc.exists) {
                reporterPhone = uDoc.data().phone || 'No phone registered';
            }
        } catch (e) { console.error(e); }
    }

    // Resolve Staff Dropdown
    let relevantStaff = [];
    if (i.assignedDepartment) {
        const deptId = i.assignedDepartment;
        relevantStaff = staff.filter(s => s.departmentId === deptId || (s.departmentName && s.departmentName.toLowerCase() === deptId.toLowerCase()));
    }
    if (!relevantStaff.length && i.category) {
        relevantStaff = staff.filter(s => s.departmentId === i.category || (s.departmentName && s.departmentName.toLowerCase() === i.category.toLowerCase()));
    }
    if (!relevantStaff.length) relevantStaff = staff;

    // Sort by Availability
    const statusOrder = { 'Available': 1, 'On Duty': 2, 'Busy': 3, 'Leave': 4, 'Offline': 5 };
    relevantStaff.sort((a, b) => {
        const sa = statusOrder[a.availabilityStatus] || 5;
        const sb = statusOrder[b.availabilityStatus] || 5;
        return sa - sb;
    });

    const isGpsVerified = i.locationType === 'GPS';
    const aiConf = i.aiConfidence ? Math.round(i.aiConfidence * 100) : 0;
    const aiConfColor = aiConf > 80 ? 'text-green-600' : (aiConf > 50 ? 'text-yellow-600' : 'text-red-600');

    document.getElementById('modalBody').innerHTML = `
        <div style="margin-bottom:20px;">
            <h4 style="margin:0 0 8px 0; color:#0f172a; font-size:18px;">${i.title}</h4>
            <p style="margin:0; color:#334155; line-height:1.6; font-size:15px;">${i.description || 'No detailed description available.'}</p>
        </div>

        <div style="display:flex; justify-content:space-between; align-items:start; margin-bottom:16px;">
            <div style="display:flex; gap:8px;">
                ${isGpsVerified ?
            '<span class="badge badge-success"><span class="material-symbols-rounded" style="font-size:14px;">my_location</span> GPS Verified</span>' :
            '<span class="badge badge-warning"><span class="material-symbols-rounded" style="font-size:14px;">location_off</span> Manual Location</span>'}
                ${i.aiGenerated ? '<span class="badge badge-info"><span class="material-symbols-rounded" style="font-size:14px;">smart_toy</span> AI Detected</span>' : ''}
            </div>
        </div>

        <div style="background:#f8fafc; padding:16px; border-radius:12px; margin-bottom:24px; border:1px solid #e2e8f0;">
            <h5 style="margin-bottom:8px; color:#64748b;">Reporter Details</h5>
            <div style="display:flex; align-items:center; gap:12px;">
                 ${i.reporterProfileImage ? `<img src="${i.reporterProfileImage}" style="width:32px; height:32px; border-radius:50%;">` : '<span class="material-symbols-rounded" style="background:#e2e8f0; padding:6px; border-radius:50%; color:#64748b;">person</span>'}
                 <div style="flex:1;">
                    <strong>${i.reporterName || 'Unknown User'}</strong>
                    <div style="font-size:12px; color:#64748b;">UID: ${i.reporterId}</div>
                    <div style="font-size:13px; color:#0f172a; margin-top:2px;">
                        <span class="material-symbols-rounded" style="font-size:14px; vertical-align:middle; color:#64748b;">call</span> 
                        ${reporterPhone}
                    </div>
                 </div>
            </div>
            ${i.userComments ? `
            <div style="margin-top:12px; padding-top:12px; border-top:1px solid #e2e8f0;">
                <strong><span class="material-symbols-rounded" style="font-size:16px; vertical-align:text-bottom;">chat</span> Additional Comments:</strong>
                <p style="margin-top:4px; font-style:italic; color:#334155;">"${i.userComments}"</p>
            </div>` : ''}
        </div>

        <div style="background:#f0f9ff; padding:16px; border-radius:12px; margin-bottom:24px; border:1px solid #bae6fd;">
            <h5 style="margin-bottom:8px; color:#0284c7;"><span class="material-symbols-rounded" style="font-size:18px; vertical-align:middle;">analytics</span> AI Analysis</h5>
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; font-size:14px;">
                <div>
                    <span style="color:#64748b;">Confidence:</span> 
                    <strong class="${aiConfColor}">${aiConf}%</strong>
                </div>
                <div>
                    <span style="color:#64748b;">Category:</span> 
                    <strong>${i.category || 'Unknown'}</strong>
                </div>
            </div>
        </div>

        <div style="margin-bottom:24px;">
            <label class="stat-label">Location Direction</label>
            <div style="display:flex; align-items:center; gap:8px; margin-top:4px;">
                <span class="material-symbols-rounded" style="color:#ef4444;">location_on</span>
                <span style="flex:1; font-size:14px;">${i.address || 'Unknown Location'}</span>
                <a href="https://www.google.com/maps/dir/?api=1&destination=${i.latitude},${i.longitude}" target="_blank" class="btn btn-sm btn-outline" style="color:#3b82f6; border-color:#3b82f6;">
                    <span class="material-symbols-rounded">directions</span> Get Directions
                </a>
            </div>
        </div>

        <div style="display:grid; grid-template-columns: 1fr 1fr; gap:16px;">
            <div>
                <label class="stat-label">Status</label>
                <select id="updateStatus" class="date" style="width:100%; padding:8px; border-radius:8px; margin-top:4px;">
                    <option value="pending" ${i.status === 'pending' ? 'selected' : ''} ${i.status === 'resolved' ? 'disabled' : ''}>Pending</option>
                    <option value="in_progress" ${i.status === 'in_progress' ? 'selected' : ''} ${i.status === 'resolved' ? 'disabled' : ''}>In Progress</option>
                    <option value="resolved" ${i.status === 'resolved' ? 'selected' : ''}>Resolved</option>
                    <option value="rejected" ${i.status === 'rejected' ? 'selected' : ''}>Rejected</option>
                </select>
            </div>
            <div>
                <label class="stat-label">Assign Staff</label>
                <select id="assignStaff" style="width:100%; padding:8px; border-radius:8px; margin-top:4px;" ${i.assignedTo ? 'disabled' : ''}>
                    <option value="">-- Select Staff --</option>
                    ${relevantStaff.map(s => {
                const st = s.availabilityStatus || 'Offline';
                return `<option value="${s.id}" ${i.assignedTo === s.id ? 'selected' : ''}>${s.name} (${st})</option>`;
            }).join('')}
                </select>
            </div>
        </div>

        <div style="margin-top:24px;">
            <h4>Evidence</h4>
            <div style="display:flex; gap:8px; overflow-x:auto; margin-top:8px;">
                ${i.images?.map(img => `<img src="${img}" style="height:100px; border-radius:8px;">`).join('')}
            </div>
        </div>
        
        ${i.proofImages?.length ? `
        <div style="margin-top:16px; background:#ecfdf5; padding:12px; border-radius:8px;">
            <h4>Proof of Work</h4>
            <p>${i.resolutionNotes || ''}</p>
            <div style="display:flex; gap:8px; overflow-x:auto; margin-top:8px;">
                 ${i.proofImages.map(img => `<img src="${img}" style="height:100px; border-radius:8px;">`).join('')}
            </div>
        </div>` : ''}

        <div class="mt-4">
            <button class="btn btn-primary" style="width:100%; justify-content:center;" onclick="saveIssue('${i.id}')">
                <span class="material-symbols-rounded">save</span> Save Changes
            </button>
        </div>
    `;
    document.getElementById('issueModal').classList.add('active');
}

async function saveIssue(id) {
    const status = document.getElementById('updateStatus').value;
    const assignedTo = document.getElementById('assignStaff').value;
    try {
        await db.collection('issues').doc(id).update({
            status,
            assignedTo: assignedTo || '',
            updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        });
        adminLogic.toast('Issue updated successfully!', 'success');
        closeModal();
    } catch (e) { adminLogic.toast(e.message, 'error'); }
}

function closeModal() { document.getElementById('issueModal').classList.remove('active'); }

function initMap() {
    map = new google.maps.Map(document.getElementById("map"), {
        zoom: 12, center: { lat: 12.9716, lng: 77.5946 }
    });
}

function updateMap(list) {
    if (!map) return;
    list.forEach(i => {
        if (i.latitude) {
            new google.maps.Marker({
                position: { lat: i.latitude, lng: i.longitude },
                map, title: i.title
            });
        }
    });
}

function toggleView(mode) {
    document.getElementById('listView').style.display = mode === 'list' ? 'block' : 'none';
    document.getElementById('mapView').style.display = mode === 'map' ? 'block' : 'none';
    document.getElementById('btnList').className = mode === 'list' ? 'btn btn-primary' : 'btn btn-outline';
    document.getElementById('btnMap').className = mode === 'map' ? 'btn btn-primary' : 'btn btn-outline';
}
